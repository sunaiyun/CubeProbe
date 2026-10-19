# SignPath Foundation 申请资料

这个文件是提交给 SignPath Foundation 的申请草稿/核对清单。申请入口：

**https://signpath.org/apply**

> SignPath 账号、GitHub App 安装和最终提交必须由仓库所有者完成；证书审批由 SignPath 决定，不保证通过。

## Project

- **Project name:** CubeProbe
- **Repository:** https://github.com/sunaiyun/CubeProbe
- **License:** MIT (OSI-approved)
- **Release:** https://github.com/sunaiyun/CubeProbe/releases/tag/v1.0.0
- **Description:** Minecraft Java/Bedrock server probe for Android, Windows, Linux, macOS and iOS. Direct Server List Ping / RakNet queries; no third-party query API.
- **Maintainer:** sunaiyun
- **Homepage / download page:** https://github.com/sunaiyun/CubeProbe
- **Code signing policy:** https://github.com/sunaiyun/CubeProbe/blob/main/CODE_SIGNING_POLICY.md

## What should be signed

- `CubeProbe-x86.exe`
- `CubeProbe-x64.exe`

Both are PyInstaller one-file Windows GUI applications built by GitHub Actions from `desktop/` in this repository. Product name: `CubeProbe`; product version: `1.0.0.0`.

## Build

- Workflow: `.github/workflows/windows-build-sign.yml`
- Runner: `windows-2022` (GitHub-hosted)
- Python: 3.8.10 x86 and x64, Tcl/Tk included
- PyInstaller: 5.13.2
- Artifact: GitHub Actions artifact `CubeProbe-windows-unsigned`
- SignPath artifact configuration: `desktop/packaging/signpath-artifact-config.xml`

## Roles

- Committers/reviewers: @sunaiyun
- Approvers: @sunaiyun

## Privacy

No telemetry. Only user-requested Minecraft server connections and DoH SRV lookups. History remains local.

## Application checklist

- [x] Public GitHub repository
- [x] OSI-approved license (MIT)
- [x] Existing release
- [x] Documented functionality
- [x] Code signing policy on project home page
- [x] MFA on GitHub account (required)
- [ ] SignPath account created
- [ ] SignPath GitHub App installed on the repository
- [ ] Project / signing policy / artifact configuration created in SignPath
- [ ] Application submitted
- [ ] Manual approval for each release

## After approval

1. Add repository secrets:
   - `SIGNPATH_API_TOKEN`
   - `SIGNPATH_ORGANIZATION_ID`
2. Adjust the slugs in `.github/workflows/windows-build-sign.yml` if needed:
   - project: `cubeprobe`
   - signing policy: `release-signing`
   - artifact configuration: `windows-exe`
3. Run **Actions → Windows x86/x64 build → Run workflow**, enable `sign`.
4. Download the `CubeProbe-windows-signed` artifact.
5. Verify with `Get-AuthenticodeSignature` and attach the signed EXEs to a new GitHub release.

## Notes

- SignPath Foundation's certificate is issued to **SignPath Foundation**; the Windows publisher will show **SignPath Foundation**, not `sunaiyun`.
- SmartScreen reputation is separate from a valid signature; a new certificate may still trigger warnings until reputation accumulates.
- SignPath may reject new projects with little public history. If rejected, alternatives are a paid OV certificate or self-signed builds for local use only.
