export function cmp<T>(l: T, r: T) {
  return l < r ? -1 : l > r ? +1 : 0;
}
