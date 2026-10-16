"""Package the built local Maven repository plus integration documentation."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
root=Path(__file__).resolve().parents[1]
repo=root/'build/maven'
if not (repo/'dev/cubiclib/cubic-ftc/0.1.0/cubic-ftc-0.1.0.aar').exists():
    raise SystemExit('Run ./gradlew publishAllPublicationsToDistributionRepository first')
out=root/'outputs';out.mkdir(exist_ok=True)
archive=out/'CubicLib-0.1.0.zip'
with ZipFile(archive,'w',ZIP_DEFLATED) as z:
    for file in sorted(repo.rglob('*')):
        if file.is_file(): z.write(file,Path('maven')/file.relative_to(repo))
    for name in ['README.md','docs/VALIDATION.md','docs/dashboard-preview.jpg','examples/CubicTeleOp.java']:
        file=root/name
        if file.exists():z.write(file,name)
(out/'CubicLib-0.1.0.sha256').write_text(hashlib.sha256(archive.read_bytes()).hexdigest()+'  '+archive.name+'\n')
print(archive)
