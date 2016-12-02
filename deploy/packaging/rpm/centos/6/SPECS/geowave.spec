%define timestamp           %(date +%Y%m%d%H%M)
%define version             %{?_version}%{!?_version: UNKNOWN}
%define vendor_version      %{?_vendor_version}%{!?_vendor_version: UNKNOWN}
%define base_name           geowave
%define name                %{base_name}-%{vendor_version}
%define versioned_app_name  %{base_name}-%{version}-%{vendor_version}
%define buildroot           %{_topdir}/BUILDROOT/%{versioned_app_name}-root
%define installpriority     %{_priority} # Used by alternatives for concurrent version installs
%define __jar_repack        %{nil}
%define _rpmfilename        %%{ARCH}/%%{NAME}.%%{RELEASE}.%%{ARCH}.rpm

%define geowave_home           /usr/local/geowave
%define geowave_install        /usr/local/%{versioned_app_name}
%define geowave_accumulo_home  %{geowave_install}/accumulo
%define geowave_hbase_home     %{geowave_install}/hbase
%define geowave_docs_home      %{geowave_install}/docs
%define geowave_geoserver_home %{geowave_install}/geoserver
%define geowave_tools_home     %{geowave_install}/tools
%define geowave_plugins_home   %{geowave_tools_home}/plugins
%define geowave_geoserver_libs %{geowave_geoserver_home}/webapps/geoserver/WEB-INF/lib
%define geowave_geoserver_data %{geowave_geoserver_home}/data_dir
%define geowave_config         /etc/geowave

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Name:           %{base_name}
Version:        %{version}
Release:        %{timestamp}
BuildRoot:      %{buildroot}
BuildArch:      noarch
Summary:        GeoWave provides geospatial and temporal indexing on top of Accumulo
License:        Apache2
Group:          Applications/Internet
Source0:        geowave-accumulo.jar
Source1:        deploy-geowave-accumulo-to-hdfs.sh
Source2:        geowave-hbase.jar
Source3:        deploy-geowave-hbase-to-hdfs.sh
Source4:        geoserver.zip
Source5:        geowave-geoserver.jar
Source6:        geowave-logrotate.sh
Source7:        geowave-init.sh
Source8:        bash_profile.sh
Source9:        default.xml
Source10:       namespace.xml
Source11:       workspace.xml
Source12:       geowave-tools.jar
Source13:       site.tar.gz
Source14:       puppet-scripts.tar.gz
Source15:       manpages.tar.gz
Source16:       plugins.tar.gz
BuildRequires:  unzip
BuildRequires:  zip
BuildRequires:  xmlto
BuildRequires:  asciidoc

%description
GeoWave provides geospatial and temporal indexing on top of Accumulo.

%prep
rm -rf %{_rpmdir}/%{buildarch}/%{versioned_app_name}*
rm -rf %{_srcrpmdir}/%{versioned_app_name}*

%build
rm -fr %{_builddir}
mkdir -p %{_builddir}/%{versioned_app_name}

%clean
rm -fr %{buildroot}
rm -fr %{_builddir}/*

%install
rm -fr %{buildroot}
mkdir -p %{buildroot}%{geowave_config}
mkdir -p %{buildroot}%{geowave_accumulo_home}

# Copy Accumulo library and deployment script onto local file system
cp %{SOURCE0} %{SOURCE1} %{buildroot}%{geowave_accumulo_home}

# Extract version info file for easy inspection
unzip -p %{SOURCE0} build.properties > %{buildroot}%{geowave_accumulo_home}/geowave-accumulo-build.properties
unzip -p %{SOURCE2} build.properties > %{buildroot}%{geowave_hbase_home}/geowave-hbase-build.properties

# Unpack and rename prepackaged jetty/geoserver
unzip -qq  %{SOURCE4} -d %{buildroot}%{geowave_install}
mv %{buildroot}%{geowave_geoserver_home}-* %{buildroot}%{geowave_geoserver_home}

# patch some config settings
sed -i 's/yyyy_mm_dd.//g' %{buildroot}%{geowave_geoserver_home}/etc/jetty.xml

# Remove cruft we don't want in our deployment
rm -fr %{buildroot}%{geowave_geoserver_home}/bin/*.bat
rm -fr %{buildroot}%{geowave_geoserver_home}/data_dir/layergroups/*
rm -fr %{buildroot}%{geowave_geoserver_home}/data_dir/workspaces/*
rm -fr %{buildroot}%{geowave_geoserver_home}/logs/keepme.txt

# Copy our geowave library into place
mkdir -p %{buildroot}%{geowave_geoserver_libs}
cp %{SOURCE5} %{buildroot}%{geowave_geoserver_libs}

# Copy system service files into place
mkdir -p %{buildroot}/etc/logrotate.d
cp %{SOURCE6} %{buildroot}/etc/logrotate.d/geowave
mkdir -p %{buildroot}/etc/init.d
cp %{SOURCE7} %{buildroot}/etc/init.d/geowave
mkdir -p %{buildroot}/etc/profile.d
cp %{SOURCE9} %{buildroot}/etc/profile.d/geowave.sh

# Copy over our custom workspace config files
mkdir -p %{buildroot}%{geowave_geoserver_data}/workspaces/geowave
cp %{SOURCE9} %{buildroot}%{geowave_geoserver_data}/workspaces
cp %{SOURCE10} %{buildroot}%{geowave_geoserver_data}/workspaces/geowave
cp %{SOURCE11} %{buildroot}%{geowave_geoserver_data}/workspaces/geowave

# Stage geowave tools
mkdir -p %{buildroot}%{geowave_plugins_home}
cp %{SOURCE12} %{buildroot}%{geowave_tools_home}
cp %{buildroot}%{geowave_accumulo_home}/geowave-accumulo-build.properties %{buildroot}%{geowave_tools_home}/build.properties
pushd %{buildroot}%{geowave_tools_home}
zip -qg %{buildroot}%{geowave_tools_home}/geowave-tools.jar build.properties
popd
mv %{buildroot}%{geowave_tools_home}/build.properties %{buildroot}%{geowave_tools_home}/geowave-tools-build.properties
unzip -p %{SOURCE12} geowave-tools.sh > %{buildroot}%{geowave_tools_home}/geowave-tools.sh
tar xzf %{SOURCE16} -C %{buildroot}%{geowave_plugins_home}

# Copy documentation into place
mkdir -p %{buildroot}%{geowave_docs_home}
tar -xzf %{SOURCE13} -C %{buildroot}%{geowave_docs_home} --strip=1

# Copy man pages into place
mkdir -p %{buildroot}/usr/local/share/man/man1
tar -xvf %{SOURCE15} -C %{buildroot}/usr/local/share/man/man1
rm -rf %{buildroot}%{geowave_docs_home}/manpages
rm -f %{buildroot}%{geowave_docs_home}/*.pdfmarks

# Puppet scripts
mkdir -p %{buildroot}/etc/puppet/modules
tar -xzf %{SOURCE14} -C %{buildroot}/etc/puppet/modules

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package -n     %{versioned_app_name}-single-host
Summary:        All GeoWave Components
Group:          Applications/Internet
Requires:       %{versioned_app_name}-accumulo = %{version}
Requires:       %{versioned_app_name}-hbase = %{version}
Requires:       %{versioned_app_name}-jetty = %{version}
Requires:       %{versioned_app_name}-tools = %{version}

%description -n %{versioned_app_name}-single-host
GeoWave provides geospatial and temporal indexing on top of Accumulo.
This package installs the accumulo, geoserver and tools components and
would likely be useful for dev environments

%files -n %{versioned_app_name}-single-host
# This is a meta-package and only exists to install other packages

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package -n     %{versioned_app_name}-accumulo
Summary:        GeoWave Accumulo Components
Group:          Applications/Internet
Provides:       %{versioned_app_name}-accumulo = %{version}
Requires:       %{versioned_app_name}-tools = %{version}
Requires:       core

%description -n %{versioned_app_name}-accumulo
GeoWave provides geospatial and temporal indexing on top of Accumulo.
This package installs the Accumulo components of GeoWave

%post -n %{versioned_app_name}-accumulo
/bin/bash %{geowave_accumulo_home}/deploy-geowave-accumulo-to-hdfs.sh >> %{geowave_accumulo_home}/geowave-accumulo-to-hdfs.log 2>&1

%files -n %{versioned_app_name}-accumulo
%defattr(644, geowave, geowave, 755)
%dir %{geowave_install}

%attr(755, hdfs, hdfs) %{geowave_accumulo_home}
%attr(644, hdfs, hdfs) %{geowave_accumulo_home}/geowave-accumulo.jar
%attr(755, hdfs, hdfs) %{geowave_accumulo_home}/deploy-geowave-accumulo-to-hdfs.sh

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package -n     %{versioned_app_name}-hbase
Summary:        GeoWave HBase Components
Group:          Applications/Internet
Provides:       %{versioned_app_name}-hbase = %{version}
Requires:       %{versioned_app_name}-tools = %{version}
Requires:       core

%description -n %{versioned_app_name}-hbase
GeoWave provides geospatial and temporal indexing on top of HBase.
This package installs the HBase components of GeoWave

%post -n %{versioned_app_name}-hbase
/bin/bash %{geowave_hbase_home}/deploy-geowave-hbase-to-hdfs.sh >> %{geowave_accumulo_home}/geowave-hbase-to-hdfs.log 2>&1

%files -n %{versioned_app_name}-hbase
%defattr(644, geowave, geowave, 755)
%dir %{geowave_install}

%attr(755, hdfs, hdfs) %{geowave_hbase_home}
%attr(644, hdfs, hdfs) %{geowave_hbase_home}/geowave-hbase.jar
%attr(755, hdfs, hdfs) %{geowave_hbase_home}/deploy-geowave-hbase-to-hdfs.sh

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package        core
Summary:        GeoWave Core
Group:          Applications/Internet
Provides:       core = %{version}

%description core
GeoWave provides geospatial and temporal indexing on top of Accumulo.
This package installs the GeoWave home directory and user account

%pre core
getent group geowave > /dev/null || /usr/sbin/groupadd -r geowave
getent passwd geowave > /dev/null || /usr/sbin/useradd --system --home /usr/local/geowave -g geowave geowave -c "GeoWave Application Account"

%postun core
if [ $1 -eq 0 ]; then
  /usr/sbin/userdel geowave
fi

%files core
%attr(644, root, root) /etc/profile.d/geowave.sh

%defattr(644, geowave, geowave, 755)
%dir %{geowave_config}

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package -n     %{versioned_app_name}-docs
Summary:        GeoWave Documentation
Group:          Applications/Internet
Provides:       %{versioned_app_name}-docs = %{version}
Requires:       %{versioned_app_name}-tools = %{version}
Requires:       core

%description -n %{versioned_app_name}-docs
GeoWave provides geospatial and temporal indexing on top of Accumulo.
This package installs the GeoWave documentation into the GeoWave directory

%files -n       %{versioned_app_name}-docs
%defattr(644, geowave, geowave, 755)
%doc %{geowave_docs_home}

%doc %defattr(644 root, root, 755)
/usr/local/share/man/man1/

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package -n     %{versioned_app_name}-jetty
Summary:        GeoWave GeoServer Components
Group:          Applications/Internet
Provides:       %{versioned_app_name}-jetty = %{version}
Requires:       %{versioned_app_name}-tools = %{version}
Requires:       core

%description -n %{versioned_app_name}-jetty
GeoWave provides geospatial and temporal indexing on top of Accumulo.
This package installs the Accumulo components of GeoWave

%post -n %{versioned_app_name}-jetty
/sbin/chkconfig --add geowave
chown -R geowave:geowave /usr/local/geowave
exit 0

%preun -n %{versioned_app_name}-jetty
/sbin/service geowave stop >/dev/null 2>&1
exit 0

%files -n %{versioned_app_name}-jetty
%defattr(644, geowave, geowave, 755) 
%{geowave_geoserver_home}

%attr(755, geowave, geowave) %{geowave_geoserver_home}/bin

%config %defattr(644, geowave, geowave, 755)
%{geowave_geoserver_home}/etc

%config %defattr(644, geowave, geowave, 755)
%{geowave_geoserver_data}

%attr(644, root, root) /etc/logrotate.d/geowave
%attr(755, root, root) /etc/init.d/geowave

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package -n     %{versioned_app_name}-tools
Summary:        GeoWave Tools
Group:          Applications/Internet
Provides:       %{versioned_app_name}-tools = %{version}
Requires:       core

%description -n %{versioned_app_name}-tools
GeoWave provides geospatial and temporal indexing on top of Accumulo.
This package installs GeoWave tools utility

%post -n %{versioned_app_name}-tools
alternatives --install %{geowave_home} geowave-home %{geowave_install} %{installpriority}
ln -fs /usr/local/geowave/tools/geowave-tools.sh /usr/local/bin/geowave
ln -fs /usr/local/geowave/tools/geowave-tools.sh /usr/local/sbin/geowave

%postun -n %{versioned_app_name}-tools
if [ $1 -eq 0 ]; then
  rm -f /usr/local/bin/geowave
  rm -f /usr/local/sbin/geowave
  alternatives --remove geowave-home %{geowave_install}
fi

%files -n %{versioned_app_name}-tools
%defattr(644, geowave, geowave, 755)
%{geowave_tools_home}

%attr(755, geowave, geowave) %{geowave_tools_home}/geowave-tools.sh

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%package        puppet
Summary:        GeoWave Puppet Scripts
Group:          Applications/Internet
Requires:       puppet

%description puppet
This package installs the geowave Puppet module to /etc/puppet/modules

%files puppet
%defattr(644, root, root, 755)
/etc/puppet/modules/geowave

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

%changelog
* Fri Nov 23 2016 Rich Fecher <rfecher@gmail.com> - 0.9.3
- Add geowave-hbase
* Fri Jun 5 2015 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.7-1
- Add external config file
* Fri May 22 2015 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.7
- Use alternatives to support parallel version and vendor installs
- Replace geowave-ingest with geowave-tools
* Thu Jan 15 2015 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.2-3
- Added man pages
* Mon Jan 5 2015 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.2-2
- Added geowave-puppet rpm
* Fri Jan 2 2015 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.2-1
- Added a helper script for geowave-ingest and bash command completion
* Wed Nov 19 2014 Andrew Spohn <andrew.e.spohn.ctr@nga.mil> - 0.8.2
- First packaging
