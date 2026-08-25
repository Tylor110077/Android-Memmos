package com.tylor.memmos.ui.fetch

/**
 * 小红书页面 DOM 提取共享定义：XhsCaptureService（后台抓取管线唯一实现）
 * 共用同一套 JS/正则，避免两处漂移。
 */
object XhsDomCapture {

    /** WebView 网络拦截匹配：小红书视频流真地址（blob 播放时 DOM/JSON 都拿不到） */
    val VIDEO_URL = Regex("sns-video|\\.mp4(\\?|$)|xhscdn\\.com/stream", RegexOption.IGNORE_CASE)

    /** 网络拦截匹配：视频封面图（sns-webpic，页面 JSON 解析失败时的兜底） */
    val COVER_URL = Regex("sns-webpic", RegexOption.IGNORE_CASE)

    /** 就绪检测：正文已渲染 + 评论数量 */
    const val READY_JS = """(function(){
  var c = document.querySelectorAll('.parent-comment, .comment-item').length;
  var d = !!document.querySelector('#detail-desc, .desc');
  return JSON.stringify({comments:c, hasDesc:d});
})()"""

    /**
     * 滚动加载评论：评论区懒加载，必须滚到底触发加载更多；楼中楼要点「查看回复/展开」
     * 才渲染。从评论元素向上找可滚动祖先滚到底，再滚整页兜底；
     * 返回 主评论+子回复 总数用于「稳定判定」。
     */
    const val SCROLL_AND_COUNT_JS = """(function(){
  function qa(s){return Array.prototype.slice.call(document.querySelectorAll(s))}
  // 展开楼中楼：短文本 + 含「查看回复/展开」的可见元素（每轮最多 20 个，防误点）
  qa('a,span,div').filter(function(el){
    var t=(el.textContent||'').trim();
    return t.length<=12 && /查看.*回复|展开/.test(t) && el.offsetParent!==null;
  }).slice(0,20).forEach(function(el){ try{el.click()}catch(e){} });
  var els = qa('.parent-comment, .comment-item');
  var p = els.length ? els[0] : null, n = 0;
  while (p && n < 12) {
    p = p.parentElement; if (!p) break; n++;
    if (p.scrollHeight > p.clientHeight + 60 && p.clientHeight > 0) p.scrollTop = p.scrollHeight;
  }
  var doc = document.scrollingElement || document.documentElement;
  doc.scrollTop = doc.scrollHeight;
  var subs = qa('.comment-item-sub');
  return JSON.stringify({comments: els.length + subs.length});
})()"""

    /** DOM 提取：多级选择器兜底 + 评论去重，返回 JSON 字符串 */
    const val EXTRACT_JS = """(function(){
  function q(s){return document.querySelector(s)}
  function qa(s){return Array.prototype.slice.call(document.querySelectorAll(s))}
  function t(el){return el?el.textContent.trim():''}
  var title = t(q('#detail-title'))||t(q('.title'))||document.title.replace(' - 小红书','');
  var desc = t(q('#detail-desc'))||t(q('.desc'))||t(q('.note-text'));
  var author = t(q('.author-container .username'))||t(q('.username'))||t(q('.name'));
  var avatarEl = q('.author-container img')||q('.avatar-item img')||q('img.avatar');
  var avatar = avatarEl?avatarEl.src:'';
  var tags = qa('.tag, .note-tag, #detail-desc a.tag').map(function(x){return x.textContent.trim().replace(/^#/,'')}).filter(function(x){return x&&x.length<30});
  var images = qa('.media-container img, .note-slider img, .swiper-slide img, .img-container img').map(function(i){return i.src}).filter(function(s){return s&&s.startsWith('http')&&s.indexOf('avatar')===-1&&s.indexOf('spectrum')===-1&&s.indexOf('notes_pre_post')===-1});
  var vEl = q('video source')||q('video');
  var video = vEl?(vEl.currentSrc||vEl.src||vEl.getAttribute('src')):'';
  if(!video||video.indexOf('blob:')===0){
    try{
      var res=(performance.getEntriesByType('resource')||[]).map(function(r){return r.name||''})
        .filter(function(n){return /sns-video|\.mp4(\?|$)|xhscdn\.com\/stream/i.test(n)});
      if(res.length)video=res[0];
    }catch(e){}
  }
  // 视频封面：登录态页面的封面在 <video poster> 上（不是 <img>，.media-container img 扫不到），
  // 置顶为第一张图——详情页与列表的封面位都取 imageUrls.firstOrNull()
  var vEl2 = q('video');
  var poster = vEl2 ? (vEl2.poster || vEl2.getAttribute('poster') || '') : '';
  if (poster && poster.indexOf('http')===0 && images.indexOf(poster)===-1) images.unshift(poster);
  // 封面兜底：资源记录里扫 sns-webpic（封面图加载请求）。
  // 排除 notes_pre_post（笔记预览卡/分享卡，不是正文封面——曾把预览卡当第一张图导致封面错位）
  var wp = [];
  try{
    wp=(performance.getEntriesByType('resource')||[]).map(function(r){return r.name||''})
      .filter(function(n){return /sns-webpic/i.test(n) && n.indexOf('avatar')===-1 && n.indexOf('notes_pre_post')===-1 && n.indexOf('spectrum')===-1});
  }catch(e){}
  if (!images.length && wp.length) images.push(wp[0]);
  // 兜底二：og:image 封面（页面 meta，与正文封面一致），并单独透出给合并层
  // （变体页 __INITIAL_STATE__ 缺失时，og:image 是唯一可靠的"本文封面"，优先于 DOM 收集结果）
  var ogImage='';
  try{
    var og=document.querySelector('meta[property="og:image"]');
    ogImage=og?(og.content||og.getAttribute('content')):'';
    if (ogImage && ogImage.indexOf('http')===0) { if(images.indexOf(ogImage)===-1) images.push(ogImage); }
    else if (ogImage && ogImage.indexOf('http')!==0) ogImage='';
  }catch(e){ogImage='';}
  // DOM 实测结构（2026-08）：.parent-comment 内 .comment-item（主）+ .comment-item.comment-item-sub（回复）。
  // 页面上没有 .sub-comment 类；两元素深度不同（主 depth2 / 回复 depth4），不能依赖 :scope（部分 WebView 不支持会抛异常
  // 导致整段提取作废降级 HTTP），改为类名过滤 + try/catch：任何选择器问题只丢评论、不丢整条笔记
  var comments = [];
  try {
    function qq(root,s){return Array.prototype.slice.call(root.querySelectorAll(s))}
    var seenC = {};
    comments = qa('.parent-comment').map(function(p){
      function qt(root,s){var e=root.querySelector(s);return e?e.textContent.trim():''}
      var all = qq(p, '.comment-item');
      if (!all.length) return null;
      var main = all.filter(function(el){ return (el.className||'').indexOf('comment-item-sub') === -1; })[0] || all[0];
      // 评论头像：实测结构是 .avatar 容器内的 img（懒加载可能走 background-image）；.author img 兜底
      function av(root){
        try{
          var i = root.querySelector('.avatar img');
          if (i && i.src && i.src.indexOf('http')===0) return i.src;
          var a2 = root.querySelector('.author img');
          if (a2 && a2.src && a2.src.indexOf('http')===0) return a2.src;
          var av = root.querySelector('.avatar');
          if (av){var bg=getComputedStyle(av).backgroundImage||''; var m=bg.match(/url\(["']?(https?:[^"')]+)/); if(m) return m[1];}
        }catch(e){}
        return '';
      }
      var seenS = {};
      var subs = all.filter(function(el){ return (el.className||'').indexOf('comment-item-sub') > -1; })
        .map(function(sc){
          return {nickname: qt(sc,'.author .name, .name'), avatar: av(sc),
                  content: qt(sc,'.note-text, .content'), likes: 0};
        }).filter(function(sc){ var k=sc.nickname+'|'+sc.content; if(seenS[k])return false; seenS[k]=1; return true; });
      return {nickname: qt(main,'.author .name, .name') || qt(p,'.author .name, .name'),
              avatar: av(main) || av(p),
              content: qt(main,'.note-text, .content') || qt(p,'.note-text, .content'),
              likes: parseInt(qt(main,'.like .count'))||0, subs: subs};
    }).filter(function(c){ return c && c.content; })
      .filter(function(c){ var k=c.nickname+'|'+c.content; if(seenC[k])return false; seenC[k]=1; return true; });
  } catch (e) { comments = []; }
  var noteId = (location.href.match(/\/(?:discovery\/item|explore)\/([a-zA-Z0-9]+)/)||[])[1]||'';
  // 评论区容器片段（调试选择器用）：往上找包含多个 .parent-comment 的祖先，截断防超大
  var scope = null;
  var p0 = qa('.parent-comment')[0];
  if (p0) {
    var c0 = p0;
    for (var k=0; k<10 && c0; k++) {
      c0 = c0.parentElement;
      if (c0 && c0.querySelectorAll('.parent-comment').length > 1) { scope = c0; break; }
    }
  }
  var commentHtml = scope ? scope.outerHTML.slice(0, 150000) : '';
  return JSON.stringify({title:title,desc:desc,author:author,avatar:avatar,tags:tags,images:images,ogImage:ogImage,video:video,comments:comments,noteId:noteId});
})()"""
}
