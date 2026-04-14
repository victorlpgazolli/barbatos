#!/bin/bash
set -e

DEB_PACKAGES_DIR=$1
REPO_DIR=$2
GPG_KEY_ID=$3
GPG_PASSPHRASE=$4
PACKAGES_BASE_URL=$5

if [ -z "$DEB_PACKAGES_DIR" ] || [ -z "$REPO_DIR" ] || [ -z "$GPG_KEY_ID" ] || [ -z "$PACKAGES_BASE_URL" ]; then
    echo "Usage: $0 <deb-packages-dir> <repo-dir> <gpg-key-id> [gpg-passphrase] <packages-base-url>"
    exit 1
fi

echo "Updating APT repository in $REPO_DIR using packages from $DEB_PACKAGES_DIR"
echo "Package download base URL: $PACKAGES_BASE_URL"

mkdir -p "$REPO_DIR/dists/stable/main/binary-amd64"
mkdir -p "$REPO_DIR/dists/stable/main/binary-arm64"

POOL_TMP=$(mktemp -d)
mkdir -p "$POOL_TMP/pool/main"
cp "$DEB_PACKAGES_DIR"/*.deb "$POOL_TMP/pool/main/"

REPO_DIR_ABS=$(realpath "$REPO_DIR")

cd "$POOL_TMP"

for arch in amd64 arm64; do
    echo "Generating Packages for $arch..."
    apt-ftparchive --arch $arch packages pool/main > "$REPO_DIR_ABS/dists/stable/main/binary-$arch/Packages"
    sed -i "s|Filename: pool/main/|Filename: $PACKAGES_BASE_URL/|g" \
        "$REPO_DIR_ABS/dists/stable/main/binary-$arch/Packages"
    gzip -c "$REPO_DIR_ABS/dists/stable/main/binary-$arch/Packages" \
        > "$REPO_DIR_ABS/dists/stable/main/binary-$arch/Packages.gz"
done

cd "$REPO_DIR_ABS"
rm -rf "$POOL_TMP"

for arch in amd64 arm64; do
    apt-ftparchive release "dists/stable/main/binary-$arch" > "dists/stable/main/binary-$arch/Release"
done

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

echo "Signing Release file..."
if [ -n "$GPG_PASSPHRASE" ]; then
    echo "$GPG_PASSPHRASE" | gpg --batch --yes --passphrase-fd 0 --default-key "$GPG_KEY_ID" --clearsign -o dists/stable/InRelease dists/stable/Release
    echo "$GPG_PASSPHRASE" | gpg --batch --yes --passphrase-fd 0 --default-key "$GPG_KEY_ID" -abs -o dists/stable/Release.gpg dists/stable/Release
else
    gpg --batch --yes --default-key "$GPG_KEY_ID" --clearsign -o dists/stable/InRelease dists/stable/Release
    gpg --batch --yes --default-key "$GPG_KEY_ID" -abs -o dists/stable/Release.gpg dists/stable/Release
fi

echo "APT repository update complete."
