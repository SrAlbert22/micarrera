"""
Publica una versión nueva de MiCarrera para TODOS los teléfonos (sin cable, sin Play Store), por etapas.

    python publicar.py 1.7 "Qué trae esta versión"     (1) prueba: solo la recibe Albert
    python publicar.py --soltar 8                        (2) cuando funciona: para todos (8 = versionCode)

Prueba:
  1. Sube versionCode (+1) y pone versionName en app/build.gradle.kts.
  2. Corre las pruebas y compila el APK firmado (release).
  3. Lo copia a la web como descargas/MiCarrera-<versionCode>.apk y publica la web en Vercel.
  4. Descarga el APK publicado y comprueba que su huella SHA-256 sea la misma.
  5. Imprime la fila para la tabla "versiones" de Supabase con solo_para = cuenta de Albert.
Soltar:
  6. Copia ese APK a descargas/MiCarrera.apk (el enlace que se comparte), publica la web y
     imprime el SQL que la suelta para todos. Así nadie baja una versión que todavía se está probando.
"""
import hashlib, re, shutil, subprocess, sys, time, urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent
WEB = RAIZ.parent / "micarrera-web"
GRADLE = RAIZ / "app" / "build.gradle.kts"
APK = RAIZ / "app" / "build" / "outputs" / "apk" / "web" / "release" / "app-web-release.apk"  # versión "web" (la de Play se genera aparte)
DESCARGAS = WEB / "public" / "descargas"
BASE = "https://micarrera-beta.vercel.app/descargas"
EQUIPO_VERCEL = "itsalberthjesus-7130s-projects"
PROBADOR = "b70f128f-b015-4c0f-be88-d92389340281"  # cuenta de Albert: recibe primero cada versión


def correr(comando, carpeta):
    print(f"\n$ {comando}")
    subprocess.run(comando, cwd=carpeta, shell=True, check=True)


def huella(datos: bytes) -> str:
    return hashlib.sha256(datos).hexdigest()


def publicar_web_y_comprobar(url: str, sha: str):
    correr(f"npx vercel deploy --prod --yes --scope {EQUIPO_VERCEL}", WEB)
    for _ in range(5):
        if huella(urllib.request.urlopen(url, timeout=60).read()) == sha:
            print("El APK publicado coincide ✓")
            return
        time.sleep(5)
    sys.exit("El APK publicado no coincide con el compilado. No guardes la versión en Supabase.")


def prueba(nombre: str, notas: str):
    original = texto = GRADLE.read_text(encoding="utf-8")
    codigo = int(re.search(r"versionCode = (\d+)", texto).group(1)) + 1
    texto = re.sub(r"versionCode = \d+", f"versionCode = {codigo}", texto)
    texto = re.sub(r'versionName = "[^"]*"', f'versionName = "{nombre}"', texto)
    GRADLE.write_text(texto, encoding="utf-8")
    print(f"Versión {nombre} (versionCode {codigo})")

    try:
        correr(f'"{RAIZ / "gradlew.bat"}" :app:testWebDebugUnitTest :app:assembleWebRelease --console=plain -q', RAIZ)
    except subprocess.CalledProcessError:
        GRADLE.write_text(original, encoding="utf-8")  # sin APK nuevo, la versión vuelve a como estaba
        sys.exit("Fallaron las pruebas o la compilación. No se publicó nada.")

    DESCARGAS.mkdir(parents=True, exist_ok=True)
    for viejo in DESCARGAS.glob("MiCarrera-*.apk"):  # solo guardamos la última de prueba (la estable es MiCarrera.apk)
        viejo.unlink()
    destino = DESCARGAS / f"MiCarrera-{codigo}.apk"
    shutil.copyfile(APK, destino)
    sha = huella(destino.read_bytes())
    print(f"APK: {destino.stat().st_size / 1e6:.1f} MB  sha256={sha}")
    url = f"{BASE}/{destino.name}"
    publicar_web_y_comprobar(url, sha)

    notas_sql = notas.replace("'", "''")
    sql = (
        "insert into public.versiones (version_code, version_name, notas, url, obligatoria, sha256, solo_para) values "
        f"({codigo}, '{nombre}', '{notas_sql}', '{url}', false, '{sha}', '{PROBADOR}');"
    )
    (RAIZ / "ultima_version.sql").write_text(sql + "\n", encoding="utf-8")
    print("\nGuardar en Supabase (solo la ve Albert, para probarla):\n" + sql)
    print(f"\nCuando funcione:  python publicar.py --soltar {codigo}")


def soltar(codigo: int):
    origen = DESCARGAS / f"MiCarrera-{codigo}.apk"
    if not origen.exists():
        sys.exit(f"No encuentro {origen.name}. ¿Seguro que la versión {codigo} es la última de prueba?")
    shutil.copyfile(origen, DESCARGAS / "MiCarrera.apk")
    sha = huella(origen.read_bytes())
    publicar_web_y_comprobar(f"{BASE}/MiCarrera.apk?v={codigo}", sha)
    print("\nSoltar para todos en Supabase:\n"
          f"update public.versiones set solo_para = null where version_code = {codigo};")


def main():
    sys.stdout.reconfigure(encoding="utf-8")  # la consola de Windows no muestra ✓ sin esto
    if len(sys.argv) == 3 and sys.argv[1] == "--soltar":
        soltar(int(sys.argv[2]))
    elif len(sys.argv) == 3:
        prueba(sys.argv[1], sys.argv[2])
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
