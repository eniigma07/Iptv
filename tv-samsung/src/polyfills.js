// Uzupełnienia dla starszych przeglądarek w telewizorach Samsung (Tizen 3.0 = Chromium 47).

if (!Object.values) {
  Object.values = function (o) {
    return Object.keys(o).map(function (k) { return o[k]; });
  };
}
if (!Object.entries) {
  Object.entries = function (o) {
    return Object.keys(o).map(function (k) { return [k, o[k]]; });
  };
}
if (!String.prototype.padStart) {
  String.prototype.padStart = function (len, fill) {
    var s = String(this);
    fill = fill === undefined ? " " : String(fill);
    while (s.length < len) s = fill + s;
    return s.slice(s.length - Math.max(len, String(this).length));
  };
}
if (window.NodeList && !NodeList.prototype.forEach) {
  NodeList.prototype.forEach = Array.prototype.forEach;
}
if (!Array.prototype.flat) {
  Array.prototype.flat = function () {
    return [].concat.apply([], this);
  };
}
if (!Array.prototype.includes) {
  Array.prototype.includes = function (v) { return this.indexOf(v) !== -1; };
}
if (!String.prototype.includes) {
  String.prototype.includes = function (v) { return this.indexOf(v) !== -1; };
}
