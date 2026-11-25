# Third-party notices

## SPMF 2.67a — GNU General Public License v3

| Item | Value |
|---|---|
| Software | SPMF (Sequential Pattern Mining Framework) 2.67a, by Philippe Fournier-Viger and contributors, https://www.philippe-fournier-viger.com/spmf/ |
| License | GNU General Public License v3 — [`third_party/spmf/LICENSE-GPL-3.0.txt`](third_party/spmf/LICENSE-GPL-3.0.txt) |
| Binary | `spmf.jar`, SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`, **unmodified** |
| Source | `spmf-2.67a-src.zip` in https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/releases/tag/spmf-2.67a — details in [`third_party/spmf/SOURCE.md`](third_party/spmf/SOURCE.md) |
| Where the binary comes from | The GitHub Release above (`spmf.jar`); CI downloads it from there (`PASPM_CI_SPMF_ARTIFACT_URL`) and refuses any file whose SHA-256 differs |

**How it is used.** PASPM runs SPMF only as a **separate process** (`java -jar spmf.jar run EFIM …` on Java 25). The PASPM backend
does not import, link or bundle SPMF classes, `pom.xml` has no SPMF dependency, and the backend JAR does not contain it.
The Docker image carries `spmf.jar` as a separate file in `/opt/paspm/`, with the license, `SOURCE.md` and this notice next to it in
`/opt/paspm/licenses/`.

**Rule for contributors.** Do not add SPMF to `pom.xml`, do not import its package, and do not copy its code into this repository.
The test `SpmfProcessBoundaryTest` fails the build if you do. A change of SPMF version needs a new decision (DEC) and a new Release.

This file is a project notice, not legal advice.
