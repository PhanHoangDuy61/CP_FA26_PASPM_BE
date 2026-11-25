# SPMF 2.67a — source and license

This note is shipped three times, with the same bytes: in the repository (`third_party/spmf/SOURCE.md`), in the GitHub Release
`spmf-2.67a`, and inside the Docker image (`/opt/paspm/licenses/spmf/SOURCE.md`).

| Item | Value |
|---|---|
| Software | SPMF (Sequential Pattern Mining Framework), version **2.67a**, by Philippe Fournier-Viger and contributors |
| Official site | https://www.philippe-fournier-viger.com/spmf/ (download page: https://www.philippe-fournier-viger.com/spmf/download.php) |
| License | GNU General Public License **v3** — `LICENSE-GPL-3.0.txt`, the copy that ships inside SPMF (SHA-256 `8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903`) |
| Binary | `spmf.jar`, 15,907,956 bytes, SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`; needs Java 25; banner `-- SPMF version 2.67a --` |
| Modification | None. The jar is redistributed exactly as downloaded from the official site on 2026-10-17 |
| Corresponding source | `spmf-2.67a-src.zip` in the same GitHub Release: 34,107,808 bytes, SHA-256 `48599087a24bd43fc1f32dac05cd2390f3895da437e092a7c0b7e6c7426a9c10`, the official source distribution (`spmf.zip`) downloaded on 2026-10-17. Its `ca/pfv/spmf/gui/Main.java` has `SPMF_VERSION = "2.67a"` |
| GitHub Release | https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/releases/tag/spmf-2.67a |

The official site now offers a newer version (2.69 on 2026-11-22), and its links always serve the newest release, so they no longer
serve 2.67a. That is why the exact 2.67a source is hosted next to the binary.

## How PASPM uses it
PASPM runs SPMF only as a **separate process** (`java -jar spmf.jar run EFIM <input> <output> …`, Java 25) and exchanges files and
command-line arguments with it. The PASPM backend does not import, link or bundle any SPMF class, and Maven does not depend on SPMF
(guarded by `SpmfProcessBoundaryTest`).
