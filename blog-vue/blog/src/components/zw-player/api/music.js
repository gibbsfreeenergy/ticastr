import axios from "axios";
const musicApiBaseUrl = (import.meta.env.VITE_MUSIC_API_BASE_URL || "").replace(/\/$/, "");

const request = path => {
  if (!musicApiBaseUrl) {
    return Promise.reject(new Error("Music API is not configured"));
  }
  return axios.post(`${musicApiBaseUrl}${path}`);
};
//获取歌词
export const getWords = id => {
  return request(`/lyric?id=${id}`);
};
//获取歌曲详情
export const getMusicInfo = id => {
  return request(`/song/detail?ids=${id}`);
};
//获取歌曲url
export const getMusicUrl = id => {
  return request(`/song/url?id=${id}`);
};
//获取热门歌曲
export const getHotMusic = id => {
  return request(`/playlist/detail?id=${id}`);
};
//获取搜索建议
export const getSearchSuggest = key => {
  return request(`/search/suggest?keywords=${key}`);
};
//获取歌曲热门评论
export const getHotTalk = id => {
  return request(`/comment/music?id=${id}&limit=3`);
};
