{
  description = "barbatos - Interactive Kotlin Native TUI debugger for Android apps via Frida";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        version = "1.0.0"; # Must be updated on release
        
        # Map Nix system architecture to our GitHub Release architecture
        archMap = {
          "x86_64-linux" = "linux-x64";
          "aarch64-linux" = "linux-arm64";
          "aarch64-darwin" = "macos-arm64";
        };
        
        targetArch = archMap.${system} or (throw "Unsupported system: ${system}");
      in
      {
        packages.default = pkgs.stdenv.mkDerivation {
          pname = "barbatos";
          inherit version;

          src = pkgs.fetchurl {
            url = "https://github.com/victorlpgazolli/barbatos/releases/download/v${version}/barbatos-${targetArch}.zip";
            # Note: During actual release, these hashes must be updated via nix-prefetch-url
            sha256 = pkgs.lib.fakeSha256; 
          };

          nativeBuildInputs = [ pkgs.unzip ];

          sourceRoot = ".";

          installPhase = ''
            mkdir -p $out/bin
            cp dist/barbatos $out/bin/barbatos
            cp dist/barbatos-bridge $out/bin/barbatos-bridge
            cp dist/barbatos-mcp $out/bin/barbatos-mcp
            chmod +x $out/bin/barbatos $out/bin/barbatos-bridge $out/bin/barbatos-mcp
          '';

          meta = with pkgs.lib; {
            description = "Interactive Kotlin Native TUI debugger for Android apps via Frida";
            homepage = "https://github.com/victorlpgazolli/barbatos";
            license = licenses.mit;
            maintainers = [ ];
          };
        };

        apps.default = flake-utils.lib.mkApp {
          drv = self.packages.${system}.default;
        };
      }
    );
}
