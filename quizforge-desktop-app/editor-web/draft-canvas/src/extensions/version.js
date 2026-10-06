/** Match the desktop release/prerelease ordering without losing precision in numeric identifiers. */
export function compareVersions(left, right) {
  const split = value => { const at=value.indexOf('-');return at<0?[value,null]:[value.slice(0,at),value.slice(at+1)]; };
  const numeric = (a,b) => { a=a.replace(/^0+(?=\d)/,'');b=b.replace(/^0+(?=\d)/,'');return a.length-b.length || (a<b?-1:a>b?1:0); };
  const [a,ap]=split(left),[b,bp]=split(right),av=a.split('.'),bv=b.split('.');
  for(let i=0;i<3;i++){const order=numeric(av[i],bv[i]);if(order)return order;}
  if(ap===null||bp===null)return ap===bp?0:ap===null?1:-1;
  const ai=ap.split('.'),bi=bp.split('.');
  for(let i=0;i<Math.min(ai.length,bi.length);i++){
    const an=/^\d+$/.test(ai[i]),bn=/^\d+$/.test(bi[i]);
    const order=an&&bn?numeric(ai[i],bi[i]):an!==bn?(an?-1:1):(ai[i]<bi[i]?-1:ai[i]>bi[i]?1:0);
    if(order)return order;
  }
  return ai.length-bi.length;
}
