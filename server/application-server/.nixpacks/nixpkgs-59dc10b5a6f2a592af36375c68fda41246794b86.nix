{ }:

let pkgs = import (fetchTarball "https://github.com/NixOS/nixpkgs/archive/59dc10b5a6f2a592af36375c68fda41246794b86.tar.gz") { overlays = [  ]; };
in with pkgs;
  let
    APPEND_LIBRARY_PATH = "${lib.makeLibraryPath [  ] }";
    myLibraries = writeText "libraries" ''
      export LD_LIBRARY_PATH="${APPEND_LIBRARY_PATH}:$LD_LIBRARY_PATH"
      
    '';
  in
    buildEnv {
      name = "59dc10b5a6f2a592af36375c68fda41246794b86-env";
      paths = [
        (runCommand "59dc10b5a6f2a592af36375c68fda41246794b86-env" { } ''
          mkdir -p $out/etc/profile.d
          cp ${myLibraries} $out/etc/profile.d/59dc10b5a6f2a592af36375c68fda41246794b86-env.sh
        '')
        jdk21 maven
      ];
    }
