export function resolveArticleId(route) {
  const articleId = route?.params?.articleId || route?.path?.split("/")[2] || null;
  return articleId && articleId !== "new" ? articleId : null;
}
