// Aplica el tema (oscuro/claro) antes de pintar la página, para que no parpadee.
// "sistema" sigue al teléfono o la computadora. Lo cambia Cuenta → Apariencia (src/tema.ts).
(function () {
  var sistemaClaro = window.matchMedia('(prefers-color-scheme: light)')
  function aplicar() {
    var tema = 'sistema'
    try { tema = localStorage.getItem('tema') || 'sistema' } catch (e) { /* sin almacenamiento */ }
    var claro = tema === 'claro' || (tema === 'sistema' && sistemaClaro.matches)
    document.documentElement.setAttribute('data-theme', claro ? 'light' : 'dark')
    var meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.setAttribute('content', claro ? '#f4f5fb' : '#12132a')
  }
  aplicar()
  if (sistemaClaro.addEventListener) sistemaClaro.addEventListener('change', aplicar)
  else sistemaClaro.addListener(aplicar)
  window.aplicarTema = aplicar
})()
