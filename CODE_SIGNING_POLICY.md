# Code signing policy

**Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by [SignPath Foundation](https://signpath.org).**

CubeProbe is an MIT-licensed open-source project. Windows release binaries are built from this repository by GitHub Actions on GitHub-hosted runners, uploaded as a workflow artifact, and submitted to SignPath for Authenticode signing.

## Roles

- **Committers / reviewers:** [@sunaiyun](https://github.com/sunaiyun)
- **Approvers:** [@sunaiyun](https://github.com/sunaiyun)

Every signing request must be manually approved by the approver after checking that:

- the workflow run corresponds to the intended `main` commit;
- the unsigned artifact was produced by the `windows-build-sign.yml` workflow;
- the version metadata matches the release.

## What is signed

Only `CubeProbe-x86.exe` and `CubeProbe-x64.exe` built from this repository are submitted. The binaries contain Python and Tcl/Tk system libraries; those upstream components are not re-signed.

## Privacy

CubeProbe does not include telemetry or analytics. It contacts only the Minecraft server address entered by the user, and may query public DNS-over-HTTPS resolvers (Cloudflare, AliDNS, Google) to resolve Java SRV records. No query history is uploaded; history is stored locally on the user's device.

## Build provenance

- Source: https://github.com/sunaiyun/CubeProbe
- Workflow: `.github/workflows/windows-build-sign.yml`
- Artifact configuration: `desktop/packaging/signpath-artifact-config.xml`
- Signing service: SignPath.io
- Certificate issuer: SignPath Foundation

## Verification

Signed Windows binaries can be checked with:

```powershell
Get-AuthenticodeSignature .\CubeProbe-x64.exe | Format-List
```

The signer should be **SignPath Foundation**, with a valid timestamp.
