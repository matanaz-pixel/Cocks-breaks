// Browser-only. Downscales to at most 1600px on the long edge and re-encodes
// as JPEG. Re-encoding through a canvas also drops all EXIF data, including
// the GPS position phones embed in photos.

const MAX_EDGE = 1600;
const QUALITY = 0.85;

type Decoded = { source: CanvasImageSource; width: number; height: number; release: () => void };

// createImageBitmap is fastest, but older Safari rejects its options and some
// browsers cannot decode every format with it. An <img> element is the most
// widely supported fallback, and modern browsers apply EXIF rotation to both.
async function decode(file: File): Promise<Decoded> {
  if (typeof createImageBitmap === "function") {
    for (const options of [{ imageOrientation: "from-image" } as const, undefined]) {
      try {
        const bitmap = await createImageBitmap(file, options);
        return { source: bitmap, width: bitmap.width, height: bitmap.height, release: () => bitmap.close() };
      } catch {
        // Try the next approach.
      }
    }
  }
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.src = url;
  try {
    await img.decode();
  } catch {
    URL.revokeObjectURL(url);
    throw new Error("לא הצלחנו לקרוא את התמונה. נסה לצלם שוב או לבחור תמונה בפורמט JPG.");
  }
  return { source: img, width: img.naturalWidth, height: img.naturalHeight, release: () => URL.revokeObjectURL(url) };
}

export async function compressImage(file: File): Promise<Blob> {
  if (file.type && !file.type.startsWith("image/")) {
    throw new Error("הקובץ שנבחר הוא לא תמונה.");
  }
  const image = await decode(file);
  const scale = Math.min(1, MAX_EDGE / Math.max(image.width, image.height));
  const width = Math.max(1, Math.round(image.width * scale));
  const height = Math.max(1, Math.round(image.height * scale));

  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    image.release();
    throw new Error("הדפדפן לא תומך בעיבוד תמונה.");
  }
  ctx.drawImage(image.source, 0, 0, width, height);
  image.release();

  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("עיבוד התמונה נכשל."))),
      "image/jpeg",
      QUALITY,
    ),
  );
}
