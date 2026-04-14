Name:           barbatos
Version:        1.0.0
Release:        1%{?dist}
Summary:        Interactive Kotlin Native TUI debugger for Android apps via Frida

License:        MIT
URL:            https://github.com/victorlpgazolli/barbatos
Source0:        https://github.com/victorlpgazolli/barbatos/releases/download/v%{version}/barbatos-linux-x64.zip

BuildRequires:  unzip
Requires:       glibc

%description
barbatos (Interactive Debug Kit) is a Kotlin Native TUI debugger for Android apps.
It provides an interactive terminal UI for live debugging via Frida.

%prep
%setup -q -c -n barbatos-%{version}

%build
# Nothing to build, we are repackaging pre-compiled binaries

%install
rm -rf $RPM_BUILD_ROOT
mkdir -p $RPM_BUILD_ROOT%{_bindir}
install -m 755 dist/barbatos $RPM_BUILD_ROOT%{_bindir}/barbatos
install -m 755 dist/barbatos-bridge $RPM_BUILD_ROOT%{_bindir}/barbatos-bridge
install -m 755 dist/barbatos-mcp $RPM_BUILD_ROOT%{_bindir}/barbatos-mcp

%files
%{_bindir}/barbatos
%{_bindir}/barbatos-bridge
%{_bindir}/barbatos-mcp

%changelog
* Tue Apr 14 2026 Victor Gazolli <victorlpgazolli@users.noreply.github.com> - 1.0.0-1
- Initial RPM release
