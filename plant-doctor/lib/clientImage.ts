// Browser-only. Downscales to at most 1600px on the long edge and re-encodes
// as JPEG. Re-encoding through a canvas also drops all EXIF data, including
// the GPS position phones embed in photos.

const MAX_EDGE = 1600;
const QUALITY = 0.85;

export async function compressImage(file: File): Promise<Blob> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    throw new Error("לא הצלחנו לקרוא את התמונה. נסה לצלם שוב או לבחור תמונה בפורמט JPG.");
  }
  const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
  const width = Math.round(bitmap.width * scale);
  const height = Math.round(bitmap.height * scale);

  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("הדפדפן לא תומך בעיבוד תמונה.");
  ctx.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();

  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("עיבוד התמונה נכשל."))),
      "image/jpeg",
      QUALITY,
    ),
  );
}
