let supported: boolean | null = null;

/**
 * Whether this browser can draw 3D (WebGL). Not everywhere: Chrome without graphics acceleration, or with a GPU it
 * blocks, has none; the viewers then fall back to pictures.
 */
export function hasWebGL(): boolean {
  if (supported == null) {
    try {
      const canvas = document.createElement("canvas");
      supported = !!(canvas.getContext("webgl2") ?? canvas.getContext("webgl"));
    } catch {
      supported = false;
    }
  }
  return supported;
}
