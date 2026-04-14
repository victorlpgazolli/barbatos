#!/bin/bash
set -e

# Usage: ./update-apt-repo.sh <deb-packages-dir> <repo-dir> <gpg-key-id> <gpg-passphrase>
DEB_PACKAGES_DIR=$1
REPO_DIR=$2
GPG_KEY_ID=$3
GPG_PASSPHRASE=$4

if [ -z "$DEB_PACKAGES_DIR" ] || [ -z "$REPO_DIR" ] || [ -z "$GPG_KEY_ID" ]; then
    echo "Usage: $0 <deb-packages-dir> <repo-dir> <gpg-key-id> [gpg-passphrase]"
    exit 1
fi

echo "Updating APT repository in $REPO_DIR using packages from $DEB_PACKAGES_DIR"

mkdir -p "$REPO_DIR/pool/main"
mkdir -p "$REPO_DIR/dists/stable/main/binary-amd64"
mkdir -p "$REPO_DIR/dists/stable/main/binary-arm64"

# Copy deb packages to pool
cp "$DEB_PACKAGES_DIR"/*.deb "$REPO_DIR/pool/main/"

cd "$REPO_DIR"

# Generate Packages files
for arch in amd64 arm64; do
    echo "Generating Packages for $arch..."
    apt-ftparchive --arch $arch packages pool/main > "dists/stable/main/binary-$arch/Packages"
    gzip -c "dists/stable/main/binary-$arch/Packages" > "dists/stable/main/binary-$arch/Packages.gz"
done

# Generate Release files for each architecture
for arch in amd64 arm64; do
    apt-ftparchive release "dists/stable/main/binary-$arch" > "dists/stable/main/binary-$arch/Release"
done

# Generate main Release file
cat > dists/stable/apt-release.conf <<EOF
APT::FTPArchive::Release::Origin "Barbatos Repository";
APT::FTPArchive::Release::Label "Barbatos";
APT::FTPArchive::Release::Suite "stable";
APT::FTPArchive::Release::Codename "stable";
APT::FTPArchive::Release::Architectures "amd64 arm64";
APT::FTPArchive::Release::Components "main";
APT::FTPArchive::Release::Description "Barbatos TUI Debugger APT Repository";
EOF

apt-ftparchive -c dists/stable/apt-release.conf release dists/stable > dists/stable/Release
rm dists/stable/apt-release.conf

# Sign the Release file
echo "Signing Release file..."
if [ -n "$GPG_PASSPHRASE" ]; then
    echo "$GPG_PASSPHRASE" | gpg --batch --yes --passphrase-fd 0 --default-key "$GPG_KEY_ID" --clearsign -o dists/stable/InRelease dists/stable/Release
    echo "$GPG_PASSPHRASE" | gpg --batch --yes --passphrase-fd 0 --default-key "$GPG_KEY_ID" -abs -o dists/stable/Release.gpg dists/stable/Release
else
    gpg --batch --yes --default-key "$GPG_KEY_ID" --clearsign -o dists/stable/InRelease dists/stable/Release
    gpg --batch --yes --default-key "$GPG_KEY_ID" -abs -o dists/stable/Release.gpg dists/stable/Release
fi

echo "APT repository update complete."
