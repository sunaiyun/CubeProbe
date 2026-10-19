"""Entry point used by the cross-platform CubeProbe.pyz bundle."""
import sys

if "--cli" in sys.argv:
    sys.argv.remove("--cli")
    from cli import main as cli_main
    raise SystemExit(cli_main())

from gui import main

main()
