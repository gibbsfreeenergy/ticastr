const backendUrl = process.env.VERCEL_BACKEND_URL?.trim();

if (!backendUrl) {
  throw new Error("VERCEL_BACKEND_URL must be configured for this Vercel project");
}

let parsedBackendUrl;
try {
  parsedBackendUrl = new URL(backendUrl);
} catch {
  throw new Error("VERCEL_BACKEND_URL must be a valid HTTPS URL");
}

if (
  parsedBackendUrl.protocol !== "https:" ||
  parsedBackendUrl.username ||
  parsedBackendUrl.password ||
  parsedBackendUrl.search ||
  parsedBackendUrl.hash
) {
  throw new Error("VERCEL_BACKEND_URL must be an HTTPS origin without credentials, query, or hash");
}

const upstream = backendUrl.replace(/\/+$/, "");

export const config = {
  rewrites: [
    { source: "/api", destination: upstream + "/" },
    { source: "/api/:path*", destination: upstream + "/:path*" },
    { source: "/uploads/:path*", destination: upstream + "/uploads/:path*" },
    { source: "/:path*", destination: "/index.html" },
  ],
};
