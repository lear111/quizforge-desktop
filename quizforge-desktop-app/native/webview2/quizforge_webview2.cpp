#define NOMINMAX
#include <windows.h>
#include <shlwapi.h>
#include <wrl.h>
#include <WebView2.h>
#include <WebView2EnvironmentOptions.h>
#include <atomic>
#include <algorithm>
#include <deque>
#include <fstream>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

using Microsoft::WRL::ComPtr;
using Microsoft::WRL::Callback;
namespace {
constexpr UINT commandMessage = WM_APP + 17;
constexpr size_t limit = 8 * 1024 * 1024; // Physical native message limit remains unchanged.
constexpr size_t chunkCharacters = 1024 * 1024;
constexpr size_t logicalLimit = 128 * 1024 * 1024;
constexpr size_t queuedCharacterLimit = 256 * 1024 * 1024;
std::atomic<unsigned long long> transportSequence{0};
std::wstring escapeJson(const std::wstring& text) {
    std::wstring result; result.reserve(text.size()+2);result.push_back(L'"');
    const wchar_t* digits=L"0123456789abcdef";
    for(wchar_t value:text) {
        if(value==L'"'||value==L'\\'){result.push_back(L'\\');result.push_back(value);}
        else if(value<32||(value>=0xd800&&value<=0xdfff)){
            result+=L"\\u";for(int shift=12;shift>=0;shift-=4)result.push_back(digits[(value>>shift)&15]);
        }else result.push_back(value);
    }
    result.push_back(L'"');return result;
}
std::vector<std::wstring> packets(const std::wstring& value) {
    if(value.size()<=chunkCharacters)return {value};
    std::vector<std::wstring> result;
    auto id=std::to_wstring(++transportSequence);auto total=(value.size()+chunkCharacters-1)/chunkCharacters;
    result.reserve(total);
    for(size_t index=0;index<total;index++)result.push_back(
        L"{\"kind\":\"qf-transport-chunk\",\"id\":\"native-"+id+L"\",\"index\":"+std::to_wstring(index)+
        L",\"total\":"+std::to_wstring(total)+L",\"length\":"+std::to_wstring(value.size())+L",\"data\":"+
        escapeJson(value.substr(index*chunkCharacters,chunkCharacters))+L"}");
    return result;
}
const std::wstring origin = L"https://quizforge.invalid/";
const std::wstring page = origin + L"webview2-practice.html";
struct Command { int kind; std::wstring value; long long id; RECT bounds; std::wstring method; std::wstring session; };
struct View {
    long long id;
    HWND parent{}, window{};
    std::wstring assets, profile;
    std::mutex mutex;
    std::deque<std::wstring> events;
    std::deque<Command> commands;
    std::atomic<bool> stopping{false};
    std::atomic<unsigned long> browserProcess{0};
    size_t eventCharacters=0, commandCharacters=0;
    bool visible=true;
    ComPtr<ICoreWebView2Environment> environment;
    ComPtr<ICoreWebView2Controller> controller;
    ComPtr<ICoreWebView2> web;
    std::vector<ComPtr<ICoreWebView2Frame>> frames;
    void emit(std::wstring event) {
        // A rejected message must never tear down the entire browser.
        if(event.size()>logicalLimit)event=L"{\"kind\":\"native-error\",\"code\":\"MESSAGE_TOO_LARGE\"}";
        auto outgoing=event.size()<=limit?std::vector<std::wstring>{event}:packets(event);size_t characters=0;for(const auto& packet:outgoing)characters+=packet.size();
        std::lock_guard lock(mutex);
        if(events.size()+outgoing.size()>512||eventCharacters+characters>queuedCharacterLimit){
            if(events.size()<512){std::wstring failure=L"{\"kind\":\"native-error\",\"code\":\"MESSAGE_QUEUE_FULL\"}";eventCharacters+=failure.size();events.push_back(std::move(failure));}return;
        }
        for(auto& packet:outgoing){eventCharacters+=packet.size();events.push_back(std::move(packet));}
    }
    void error(const wchar_t* code) { emit(L"{\"kind\":\"native-error\",\"code\":\"" + std::wstring(code) + L"\"}"); }
};
std::mutex registryMutex;
std::map<long long, std::shared_ptr<View>> registry;
std::atomic<long long> sequence{0};
std::shared_ptr<View> find(long long id) { std::lock_guard lock(registryMutex); auto it=registry.find(id); return it==registry.end()?nullptr:it->second; }
bool allowedAsset(const std::wstring& uri, std::wstring& name) {
    if (uri.rfind(origin,0)!=0) return false;
    name=uri.substr(origin.size());
    return name==L"webview2-practice.html" || name==L"webview2-practice.js" || name==L"webview2-practice.css";
}
void resource(View* view, ICoreWebView2WebResourceRequestedEventArgs* args) {
    ComPtr<ICoreWebView2WebResourceRequest> request; args->get_Request(&request);
    LPWSTR uri=nullptr, method=nullptr; request->get_Uri(&uri);request->get_Method(&method);
    std::wstring name;bool allowed=uri && method && std::wstring(method)==L"GET" && allowedAsset(uri,name);
    CoTaskMemFree(uri);CoTaskMemFree(method);
    std::vector<unsigned char> bytes;
    if(allowed) {
        std::ifstream input(view->assets+L"\\"+name,std::ios::binary);
        if(input) { input.seekg(0,std::ios::end);auto size=input.tellg();input.seekg(0);
            if(size>=0 && size<=static_cast<std::streamoff>(limit)){bytes.resize(static_cast<size_t>(size));input.read(reinterpret_cast<char*>(bytes.data()),size);allowed=static_cast<bool>(input);}else allowed=false;
        } else allowed=false;
    }
    ComPtr<IStream> stream;stream.Attach(SHCreateMemStream(bytes.data(),static_cast<UINT>(bytes.size())));
    std::wstring content=name.ends_with(L".js")?L"text/javascript; charset=utf-8":name.ends_with(L".css")?L"text/css; charset=utf-8":L"text/html; charset=utf-8";
    std::wstring headers=L"Content-Type: "+content+L"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n";
    ComPtr<ICoreWebView2WebResourceResponse> response;
    view->environment->CreateWebResourceResponse(stream.Get(),allowed?200:403,allowed?L"OK":L"Forbidden",headers.c_str(),&response);
    args->put_Response(response.Get());
}
HRESULT configure(View* view) {
    HRESULT hr=view->controller->get_CoreWebView2(&view->web);if(FAILED(hr))return hr;
    UINT32 process=0;view->web->get_BrowserProcessId(&process);view->browserProcess=process;
    ComPtr<ICoreWebView2Settings> settings;view->web->get_Settings(&settings);
    settings->put_AreHostObjectsAllowed(FALSE);settings->put_AreDefaultScriptDialogsEnabled(FALSE);
    settings->put_AreDefaultContextMenusEnabled(FALSE);settings->put_AreDevToolsEnabled(FALSE);
    settings->put_IsStatusBarEnabled(FALSE);settings->put_IsWebMessageEnabled(TRUE);
    ComPtr<ICoreWebView2Settings3> settings3;
    if(SUCCEEDED(settings.As(&settings3)))settings3->put_AreBrowserAcceleratorKeysEnabled(FALSE);
    EventRegistrationToken token;
    view->web->add_NavigationStarting(Callback<ICoreWebView2NavigationStartingEventHandler>([](ICoreWebView2*,ICoreWebView2NavigationStartingEventArgs* args)->HRESULT{
        LPWSTR uri=nullptr;args->get_Uri(&uri);args->put_Cancel(!uri || std::wstring(uri)!=page);CoTaskMemFree(uri);return S_OK;
    }).Get(),&token);
    view->web->add_FrameNavigationStarting(Callback<ICoreWebView2NavigationStartingEventHandler>([](ICoreWebView2*,ICoreWebView2NavigationStartingEventArgs* args)->HRESULT{
        LPWSTR uri=nullptr;args->get_Uri(&uri);bool allowed=uri&&(std::wstring(uri)==L"about:srcdoc"||std::wstring(uri)==L"about:blank");args->put_Cancel(!allowed);CoTaskMemFree(uri);return S_OK;
    }).Get(),&token);
    view->web->add_NewWindowRequested(Callback<ICoreWebView2NewWindowRequestedEventHandler>([](ICoreWebView2*,ICoreWebView2NewWindowRequestedEventArgs* args)->HRESULT{args->put_Handled(TRUE);return S_OK;}).Get(),&token);
    view->web->add_PermissionRequested(Callback<ICoreWebView2PermissionRequestedEventHandler>([](ICoreWebView2*,ICoreWebView2PermissionRequestedEventArgs* args)->HRESULT{args->put_State(COREWEBVIEW2_PERMISSION_STATE_DENY);return S_OK;}).Get(),&token);
    ComPtr<ICoreWebView2_4> web4;if(FAILED(view->web.As(&web4)))return E_NOINTERFACE;
    web4->add_DownloadStarting(Callback<ICoreWebView2DownloadStartingEventHandler>([](ICoreWebView2*,ICoreWebView2DownloadStartingEventArgs* args)->HRESULT{args->put_Cancel(TRUE);args->put_Handled(TRUE);return S_OK;}).Get(),&token);
    web4->add_FrameCreated(Callback<ICoreWebView2FrameCreatedEventHandler>([view](ICoreWebView2*,ICoreWebView2FrameCreatedEventArgs* args)->HRESULT{
        view->frames.erase(std::remove_if(view->frames.begin(),view->frames.end(),[](auto& frame){BOOL destroyed=TRUE;frame->IsDestroyed(&destroyed);return destroyed!=FALSE;}),view->frames.end());
        ComPtr<ICoreWebView2Frame> frame;args->get_Frame(&frame);view->frames.push_back(frame);return S_OK;
    }).Get(),&token);
    view->web->AddWebResourceRequestedFilter(L"*",COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL);
    view->web->add_WebResourceRequested(Callback<ICoreWebView2WebResourceRequestedEventHandler>([view](ICoreWebView2*,ICoreWebView2WebResourceRequestedEventArgs* args)->HRESULT{resource(view,args);return S_OK;}).Get(),&token);
    // Receive top-level messages only. No host object or frame message handler is installed.
    view->web->add_WebMessageReceived(Callback<ICoreWebView2WebMessageReceivedEventHandler>([view](ICoreWebView2*,ICoreWebView2WebMessageReceivedEventArgs* args)->HRESULT{
        LPWSTR source=nullptr;args->get_Source(&source);bool trusted=source&&std::wstring(source)==page;CoTaskMemFree(source);
        if(trusted){LPWSTR json=nullptr;if(SUCCEEDED(args->get_WebMessageAsJson(&json))&&json){view->emit(json);CoTaskMemFree(json);}}return S_OK;
    }).Get(),&token);
    view->web->add_ProcessFailed(Callback<ICoreWebView2ProcessFailedEventHandler>([view](ICoreWebView2*,ICoreWebView2ProcessFailedEventArgs*)->HRESULT{view->error(L"RENDERER_FAILED");return S_OK;}).Get(),&token);
    view->web->add_NavigationCompleted(Callback<ICoreWebView2NavigationCompletedEventHandler>([view](ICoreWebView2*,ICoreWebView2NavigationCompletedEventArgs* args)->HRESULT{
        BOOL ok=FALSE;args->get_IsSuccess(&ok);if(!ok)view->error(L"NAVIGATION_FAILED");return S_OK;
    }).Get(),&token);
    RECT bounds;GetClientRect(view->window,&bounds);view->controller->put_Bounds(bounds);
    view->controller->put_IsVisible(view->visible?TRUE:FALSE);
    return view->web->Navigate(page.c_str());
}
void commands(View* view) {
    std::deque<Command> pending;{std::lock_guard lock(view->mutex);pending.swap(view->commands);view->commandCharacters=0;}
    for(auto& c:pending) {
        if(c.kind==3){DestroyWindow(view->window);break;}
        if(c.kind==2){SetWindowPos(view->window,nullptr,c.bounds.left,c.bounds.top,c.bounds.right,c.bounds.bottom,SWP_NOACTIVATE|SWP_NOZORDER);continue;}
        if(c.kind==7){view->visible=c.id!=0;ShowWindow(view->window,view->visible?SW_SHOWNA:SW_HIDE);if(view->controller)view->controller->put_IsVisible(view->visible?TRUE:FALSE);continue;}
        if(!view->web){view->error(L"NOT_READY");continue;}
        if(c.kind==6) {
            if(c.id==0&&!c.bounds.right){SetForegroundWindow(view->parent);view->controller->MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC);}
            double scale=static_cast<double>(GetDpiForWindow(view->window))/96.0;
            ComPtr<ICoreWebView2Controller3> controller3;
            if(SUCCEEDED(view->controller.As(&controller3)))controller3->get_RasterizationScale(&scale);
            POINT position{static_cast<LONG>(c.bounds.left*scale),static_cast<LONG>(c.bounds.top*scale)};
            ClientToScreen(view->window,&position);
            auto target=WindowFromPoint(position);
            if(target!=view->window&&!IsChild(view->window,target)){view->error(L"INPUT_WINDOW_OBSCURED");continue;}
            INPUT input{};input.type=INPUT_MOUSE;
            int x=GetSystemMetrics(SM_XVIRTUALSCREEN),y=GetSystemMetrics(SM_YVIRTUALSCREEN);
            input.mi.dx=static_cast<LONG>((position.x-x)*65535.0/(GetSystemMetrics(SM_CXVIRTUALSCREEN)-1));
            input.mi.dy=static_cast<LONG>((position.y-y)*65535.0/(GetSystemMetrics(SM_CYVIRTUALSCREEN)-1));
            input.mi.dwFlags=MOUSEEVENTF_MOVE|MOUSEEVENTF_ABSOLUTE|MOUSEEVENTF_VIRTUALDESK;
            if(c.id==1)input.mi.dwFlags|=MOUSEEVENTF_LEFTDOWN;
            if(c.id==2)input.mi.dwFlags|=MOUSEEVENTF_LEFTUP;
            if(SendInput(1,&input,sizeof(input))!=1)view->error(L"INPUT_FAILED");continue;
        }
        if(c.kind==0)for(const auto& packet:packets(c.value)){
            if(packet.size()>limit||FAILED(view->web->PostWebMessageAsJson(packet.c_str()))){view->error(L"POST_MESSAGE_FAILED");break;}
        }
        if(c.kind==1) view->web->ExecuteScript(c.value.c_str(),Callback<ICoreWebView2ExecuteScriptCompletedHandler>([view,id=c.id](HRESULT hr,LPCWSTR json)->HRESULT{
            view->emit(L"{\"kind\":\"evaluation\",\"id\":"+std::to_wstring(id)+L",\"ok\":"+(SUCCEEDED(hr)?L"true":L"false")+L",\"value\":"+(json?json:L"null")+L"}");return S_OK;
        }).Get());
        if(c.kind==4) {
            auto callback=Callback<ICoreWebView2CallDevToolsProtocolMethodCompletedHandler>([view,id=c.id](HRESULT hr,LPCWSTR json)->HRESULT{
            view->emit(L"{\"kind\":\"evaluation\",\"id\":"+std::to_wstring(id)+L",\"ok\":"+(SUCCEEDED(hr)?L"true":L"false")+L",\"value\":"+(json?json:L"null")+L"}");return S_OK;
            });
            if(c.session.empty())view->web->CallDevToolsProtocolMethod(c.method.c_str(),c.value.c_str(),callback.Get());
            else {ComPtr<ICoreWebView2_11> web11;if(SUCCEEDED(view->web.As(&web11)))web11->CallDevToolsProtocolMethodForSession(c.session.c_str(),c.method.c_str(),c.value.c_str(),callback.Get());else callback->Invoke(E_NOINTERFACE,nullptr);}
        }
        if(c.kind==5) {
            view->frames.erase(std::remove_if(view->frames.begin(),view->frames.end(),[](auto& frame){BOOL destroyed=TRUE;frame->IsDestroyed(&destroyed);return destroyed!=FALSE;}),view->frames.end());
            ComPtr<ICoreWebView2Frame2> frame;
            if(view->frames.empty()||FAILED(view->frames.back().As(&frame))){view->emit(L"{\"kind\":\"evaluation\",\"id\":"+std::to_wstring(c.id)+L",\"ok\":false,\"value\":null}");continue;}
            frame->ExecuteScript(c.value.c_str(),Callback<ICoreWebView2ExecuteScriptCompletedHandler>([view,id=c.id](HRESULT hr,LPCWSTR json)->HRESULT{
                view->emit(L"{\"kind\":\"evaluation\",\"id\":"+std::to_wstring(id)+L",\"ok\":"+(SUCCEEDED(hr)?L"true":L"false")+L",\"value\":"+(json?json:L"null")+L"}");return S_OK;
            }).Get());
        }
    }
}
LRESULT CALLBACK windowProc(HWND window,UINT message,WPARAM w,LPARAM l) {
    auto view=reinterpret_cast<View*>(GetWindowLongPtr(window,GWLP_USERDATA));
    if(message==WM_NCCREATE){view=static_cast<View*>(reinterpret_cast<CREATESTRUCT*>(l)->lpCreateParams);SetWindowLongPtr(window,GWLP_USERDATA,reinterpret_cast<LONG_PTR>(view));}
    if(view){if(message==commandMessage){commands(view);return 0;}if(message==WM_SIZE&&view->controller){RECT r;GetClientRect(window,&r);view->controller->put_Bounds(r);return 0;}if(message==WM_SETFOCUS&&view->controller)view->controller->MoveFocus(COREWEBVIEW2_MOVE_FOCUS_REASON_PROGRAMMATIC);if(message==WM_DESTROY){PostQuitMessage(0);return 0;}}
    return DefWindowProc(window,message,w,l);
}
void run(std::shared_ptr<View> view) {
    SetThreadDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    HRESULT hr=CoInitializeEx(nullptr,COINIT_APARTMENTTHREADED);
    if(FAILED(hr)){view->error(L"COM_FAILED");std::lock_guard lock(registryMutex);registry.erase(view->id);return;}
    WNDCLASS wc{};wc.lpfnWndProc=windowProc;wc.hInstance=GetModuleHandle(nullptr);wc.lpszClassName=L"QuizForge.WebView2";RegisterClass(&wc);
    RECT rect;GetClientRect(view->parent,&rect);
    auto window=CreateWindowEx(0,wc.lpszClassName,L"",WS_CHILD|WS_VISIBLE|WS_CLIPCHILDREN|WS_CLIPSIBLINGS,0,0,rect.right,rect.bottom,view->parent,nullptr,wc.hInstance,view.get());
    {std::lock_guard lock(view->mutex);view->window=window;}
    if(!window){view->error(L"WINDOW_FAILED");CoUninitialize();std::lock_guard lock(registryMutex);registry.erase(view->id);return;}
    if(view->stopping)PostMessage(window,WM_CLOSE,0,0);
    auto options=Microsoft::WRL::Make<CoreWebView2EnvironmentOptions>();
    options->put_AdditionalBrowserArguments(L"--disable-background-networking --disable-component-update --disable-sync --no-first-run");
    hr=CreateCoreWebView2EnvironmentWithOptions(nullptr,view->profile.c_str(),options.Get(),Callback<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>([view](HRESULT result,ICoreWebView2Environment* environment)->HRESULT{
        if(view->stopping)return S_OK;
        if(FAILED(result)||!environment){view->error(L"ENVIRONMENT_FAILED");return S_OK;}
        view->environment=environment;
        return environment->CreateCoreWebView2Controller(view->window,Callback<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler>([view](HRESULT result,ICoreWebView2Controller* controller)->HRESULT{
            if(view->stopping){if(controller)controller->Close();return S_OK;}
            if(FAILED(result)||!controller){view->error(L"CONTROLLER_FAILED");return S_OK;}
            view->controller=controller;if(FAILED(configure(view.get())))view->error(L"CONFIGURATION_FAILED");return S_OK;
        }).Get());
    }).Get());
    if(FAILED(hr))view->error(L"RUNTIME_UNAVAILABLE");
    MSG msg;while(GetMessage(&msg,nullptr,0,0)>0){TranslateMessage(&msg);DispatchMessage(&msg);}
    view->stopping=true;if(view->controller)view->controller->Close();view->frames.clear();view->web.Reset();view->controller.Reset();view->environment.Reset();
    CoUninitialize();std::lock_guard lock(registryMutex);registry.erase(view->id);
}
bool enqueue(long long id,Command command) {
    auto view=find(id);if(!view)return false;
    std::lock_guard lock(view->mutex);if(view->stopping||!view->window||view->commands.size()>=128||command.value.size()>(command.kind==0?logicalLimit:limit)||view->commandCharacters+command.value.size()>queuedCharacterLimit)return false;
    if(command.kind==3)view->stopping=true;
    view->commandCharacters+=command.value.size();view->commands.push_back(std::move(command));return PostMessage(view->window,commandMessage,0,0)!=FALSE;
}
}
extern "C" __declspec(dllexport) long long qf_create(HWND parent,LPCWSTR assets,LPCWSTR profile) {
    if(!IsWindow(parent)||!assets||!profile)return 0;
    auto view=std::make_shared<View>();view->id=++sequence;view->parent=parent;view->assets=assets;view->profile=profile;
    {std::lock_guard lock(registryMutex);registry[view->id]=view;}
    std::thread(run,view).detach();return view->id;
}
extern "C" __declspec(dllexport) int qf_post(long long id,LPCWSTR json) {return json&&enqueue(id,{0,json,0,{}});}
extern "C" __declspec(dllexport) int qf_eval(long long id,long long request,LPCWSTR source) {return source&&enqueue(id,{1,source,request,{}});}
extern "C" __declspec(dllexport) int qf_eval_frame(long long id,long long request,LPCWSTR source) {return source&&enqueue(id,{5,source,request,{}});}
extern "C" __declspec(dllexport) int qf_mouse(long long id,int action,int x,int y,int held) {return enqueue(id,{6,L"",action,{x,y,held,0}});}
// Only the trusted Java verification driver uses this. It is never published in a page bridge.
extern "C" __declspec(dllexport) int qf_cdp(long long id,long long request,LPCWSTR method,LPCWSTR parameters) {return method&&parameters&&enqueue(id,{4,parameters,request,{},method});}
extern "C" __declspec(dllexport) int qf_cdp_session(long long id,long long request,LPCWSTR session,LPCWSTR method,LPCWSTR parameters) {return session&&method&&parameters&&enqueue(id,{4,parameters,request,{},method,session});}
extern "C" __declspec(dllexport) int qf_bounds(long long id,int x,int y,int width,int height) {return enqueue(id,{2,L"",0,{x,y,width,height}});}
extern "C" __declspec(dllexport) int qf_visible(long long id,int visible){return enqueue(id,{7,L"",visible,{}});}
extern "C" __declspec(dllexport) int qf_visibility(long long id){auto view=find(id);if(!view)return 0;std::lock_guard lock(view->mutex);return view->window&&IsWindowVisible(view->window);}
extern "C" __declspec(dllexport) int qf_close(long long id) {
    auto view=find(id);if(!view)return 1;
    std::lock_guard lock(view->mutex);view->stopping=true;
    return !view->window||PostMessage(view->window,WM_CLOSE,0,0)!=FALSE;
}
extern "C" __declspec(dllexport) int qf_poll(long long id,wchar_t* buffer,int capacity) {
    auto view=find(id);if(!view||!buffer||capacity<1)return 0;
    std::lock_guard lock(view->mutex);if(view->events.empty())return 0;
    auto& value=view->events.front();if(value.size()+1>static_cast<size_t>(capacity))return -static_cast<int>(value.size()+1);
    memcpy(buffer,value.c_str(),(value.size()+1)*sizeof(wchar_t));int count=static_cast<int>(value.size());view->eventCharacters-=value.size();view->events.pop_front();return count;
}

// Trusted lifecycle queries; never exposed through the extension SDK.
extern "C" __declspec(dllexport) unsigned long qf_browser_process(long long id){auto view=find(id);return view?view->browserProcess.load():0;}
extern "C" __declspec(dllexport) int qf_exists(long long id){return find(id)?1:0;}
