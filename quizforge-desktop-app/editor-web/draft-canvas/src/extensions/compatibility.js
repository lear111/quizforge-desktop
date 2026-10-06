export const hostSdk=Object.freeze({apiMajor:2,apiMinor:1,packageFormatVersion:2});
export function requireCompatibleManifest(manifest){
  if(!manifest||typeof manifest!=='object')throw new TypeError('Missing extension manifest');
  const format=manifest.packageFormatVersion,major=manifest.sdkApiMajor,minor=manifest.minSdkApiMinor??0;
  if(!Number.isInteger(format)||!Number.isInteger(major)||!Number.isInteger(minor)||minor<0)
    throw new TypeError('packageFormatVersion/sdkApiMajor/minSdkApiMinor must be integers; minSdkApiMinor must be nonnegative');
  if(format!==hostSdk.packageFormatVersion)throw new TypeError(`扩展需要格式 ${format}，当前支持格式 2；${format>2?'请更新应用':'请更新扩展为 HTML/JSON 格式 2'}`);
  if(major!==hostSdk.apiMajor)throw new TypeError(`扩展需要 SDK ${major}，当前 SDK 2.1；${major>2?'请更新应用':'HTML SDK 2 required，请更新扩展'}`);
  if(minor>hostSdk.apiMinor)throw new TypeError(`扩展需要 SDK 2.${minor}，当前 SDK 2.1；请更新应用`);
}
