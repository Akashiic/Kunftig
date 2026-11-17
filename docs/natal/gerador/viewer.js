// Builds a three.js scene from the preview JSON written by bbtool.py (Blockbench conventions: pivots in pixels,
// Euler rotations in degrees applied Z, Y, X; box UV faces drawn as seen from outside). Shared by the offline
// renderer (render.mjs) and the published viewer page.
export function buildModel(THREE, model, opts = {}) {
  const loader = new THREE.TextureLoader();
  const tex = loader.load(model.texture);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.generateMipmaps = false;
  tex.colorSpace = THREE.SRGBColorSpace;
  const mat = new THREE.MeshBasicMaterial({ map: tex, vertexColors: true, alphaTest: 0.5, side: THREE.DoubleSide });
  const W = model.tex_w, H = model.tex_h;
  const root = new THREE.Group();
  const groups = {};
  const d2r = Math.PI / 180;
  const pivots = {};
  for (const b of model.bones) pivots[b.name] = b.pivot;
  for (const b of model.bones) {
    const g = new THREE.Group();
    const pp = b.parent ? pivots[b.parent] : [0, 0, 0];
    g.position.set(b.pivot[0] - pp[0], b.pivot[1] - pp[1], b.pivot[2] - pp[2]);
    g.rotation.set(b.rot[0] * d2r, b.rot[1] * d2r, b.rot[2] * d2r, 'ZYX');
    g.name = b.name;
    groups[b.name] = g;
    (b.parent ? groups[b.parent] : root).add(g);
    for (const cb of b.cubes) {
      let holder = g, base = b.pivot;
      if (cb.rot) {
        holder = new THREE.Group();
        holder.position.set(cb.origin[0] - b.pivot[0], cb.origin[1] - b.pivot[1], cb.origin[2] - b.pivot[2]);
        holder.rotation.set(cb.rot[0] * d2r, cb.rot[1] * d2r, cb.rot[2] * d2r, 'ZYX');
        g.add(holder);
        base = cb.origin;
      }
      holder.add(cubeMesh(THREE, cb, base, mat, W, H));
    }
  }
  return { root, groups };
}

function cubeMesh(THREE, cb, base, mat, W, H) {
  const [w, h, d] = cb.size, i = cb.inflate || 0;
  const x1 = cb.frm[0] - i - base[0], y1 = cb.frm[1] - i - base[1], z1 = cb.frm[2] - i - base[2];
  const x2 = cb.frm[0] + w + i - base[0], y2 = cb.frm[1] + h + i - base[1], z2 = cb.frm[2] + d + i - base[2];
  const [u, v] = cb.uv;
  const R = {
    north: [u + d, v + d, w, h], east: [u, v + d, d, h], south: [u + 2 * d + w, v + d, w, h],
    west: [u + d + w, v + d, d, h], up: [u + d, v, w, d], down: [u + d + w, v, w, d],
  };
  // corners: top-left, top-right, bottom-left, bottom-right of the face seen from outside
  const F = {
    north: [[x2, y2, z1], [x1, y2, z1], [x2, y1, z1], [x1, y1, z1], [0, 0, -1]],
    south: [[x1, y2, z2], [x2, y2, z2], [x1, y1, z2], [x2, y1, z2], [0, 0, 1]],
    east: [[x2, y2, z2], [x2, y2, z1], [x2, y1, z2], [x2, y1, z1], [1, 0, 0]],
    west: [[x1, y2, z1], [x1, y2, z2], [x1, y1, z1], [x1, y1, z2], [-1, 0, 0]],
    up: [[x2, y2, z2], [x1, y2, z2], [x2, y2, z1], [x1, y2, z1], [0, 1, 0]],
    down: [[x2, y1, z1], [x1, y1, z1], [x2, y1, z2], [x1, y1, z2], [0, -1, 0]],
  };
  const pos = [], uvs = [], nor = [], idx = [];
  let n = 0;
  for (const k of Object.keys(F)) {
    const [tl, tr, bl, br, nn] = F[k];
    const [rx, ry, rw, rh] = R[k];
    const U0 = rx / W, U1 = (rx + rw) / W, V0 = 1 - ry / H, V1 = 1 - (ry + rh) / H;
    pos.push(...tl, ...tr, ...bl, ...br);
    uvs.push(U0, V0, U1, V0, U0, V1, U1, V1);
    for (let q = 0; q < 4; q++) nor.push(...nn);
    idx.push(n, n + 2, n + 1, n + 1, n + 2, n + 3);
    n += 4;
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  geo.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
  geo.setAttribute('color', new THREE.Float32BufferAttribute(new Array(pos.length).fill(1), 3));
  geo.setIndex(idx);
  return new THREE.Mesh(geo, mat);
}

// Flat Blockbench-like shading: each face gets a brightness from its world normal.
export function shadeScene(THREE, root) {
  root.updateMatrixWorld(true);
  const L = new THREE.Vector3(0.35, 0.85, -0.45).normalize();
  const nm = new THREE.Matrix3(), n = new THREE.Vector3();
  root.traverse((o) => {
    if (!o.isMesh) return;
    nm.getNormalMatrix(o.matrixWorld);
    const N = o.geometry.attributes.normal, C = o.geometry.attributes.color;
    for (let k = 0; k < N.count; k++) {
      n.fromBufferAttribute(N, k).applyMatrix3(nm).normalize();
      const b = 0.62 + 0.38 * Math.max(0, n.dot(L)) + 0.06 * n.y;
      C.setXYZ(k, b, b, b);
    }
    C.needsUpdate = true;
  });
}
