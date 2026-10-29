from pathlib import Path
import zipfile,hashlib,urllib.request,io
# User accepted ITF FFL v2 on 29 Oct 2026. Publisher fonts are embedded unchanged,
# and are never committed as standalone files to the public repository.
FILES={
 'ClashGrotesk-Regular.otf':('clash_regular.otf','0d314eca15d9c38f6e9fc24eb60676ce09241eef0884a44fdfa3030cd03ef783'),
 'ClashGrotesk-Semibold.otf':('clash_semibold.otf','6c2ba09a7299ff5965d80c34d0f1a3bd980a0d9a04f2cc5f45d026381372573a'),
 'ClashGrotesk-Bold.otf':('clash_bold.otf','070299e5789300eb054b31b599a948604b8fd189389c5cb86611ef824c6515a1')}
def prepare(blob):
 root=Path('app/src/main'); (root/'res/font').mkdir(parents=True,exist_ok=True);(root/'assets').mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(io.BytesIO(blob)) as archive:
  for original,(destination,digest) in FILES.items():
   path=next(p for p in archive.namelist() if p.endswith('/Fonts/OTF/'+original))
   content=archive.read(path)
   assert hashlib.sha256(content).hexdigest()==digest, 'Font checksum changed: '+original
   (root/'res/font'/destination).write_bytes(content)
   print('Verified official font:',original,digest)
  license_path=next(p for p in archive.namelist() if p.endswith('/License/FFL.txt'))
  license_bytes=archive.read(license_path)
  assert hashlib.sha256(license_bytes).hexdigest()=='145e7fe2429a3336ba215c070ef722000e01348a3e1baaa127e871bb5012f554', 'Publisher license changed; review required'
  (root/'assets/ClashGrotesk-FFL.txt').write_bytes(license_bytes)
if __name__=='__main__':
 import sys
 if len(sys.argv)>1:blob=Path(sys.argv[1]).read_bytes()
 else:
  request=urllib.request.Request('https://api.fontshare.com/v2/fonts/download/clash-grotesk',headers={'User-Agent':'WorkMeter-build/2.2'})
  with urllib.request.urlopen(request,timeout=90) as response:blob=response.read()
 prepare(blob)

