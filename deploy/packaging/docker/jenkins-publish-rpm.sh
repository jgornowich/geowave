#!/bin/bash
#
# For use by rpm building jenkins jobs. Handles job race conditions and
# reindexing the existing rpm repo
#

set -x

echo '###### Build Variables'

declare -A ARGS
while [ $# -gt 0 ]; do
    # Trim the first two chars off of the arg name ex: --foo
    case "$1" in
        *) NAME="${1:2}"; shift; ARGS[$NAME]="$1" ;;
    esac
    shift
done
echo '###### Build tarball distribution archive'

# Copy the SRPM into an extract directory
mkdir -p ${WORKSPACE}/${ARGS[buildroot]}/TARBALL/geowave
cp ${WORKSPACE}/${ARGS[buildroot]}/SRPMS/*.rpm ${WORKSPACE}/${ARGS[buildroot]}/TARBALL/geowave
cd ${WORKSPACE}/${ARGS[buildroot]}/TARBALL/geowave

# Extract all the files
rpm2cpio *.rpm | cpio -idmv

# Remove what we don't want to distribute within the tarball
rm -f *.rpm *.xml *.spec

# Extract the build metadata from one of the artifacts
unzip -p geowave-accumulo-${ARGS[vendor-version]}.jar build.properties > build.properties

# Extract the pdf version of the docs so it's more visibly available
tar xzf site.tar.gz --strip-components=1  site/documentation.pdf

# Archive things, copy some artifacts up to AWS if available and get rid of our temp area
cd ..
githash=$(cat geowave/build.properties | grep project.scm.revision | sed -e 's/project.scm.revision=//g')
version=$(cat geowave/build.properties | grep project.version | sed -e 's/project.version=//g')
tar cvzf geowave-${ARGS[vendor-version]}-$version-${githash:0:7}.tar.gz geowave

rm -rf geowave

# Copy the Jace artifacts
cp ${WORKSPACE}/${ARGS[buildroot]}/SOURCES/geowave-jace-${ARGS[vendor-version]}.tar.gz ${WORKSPACE}/${ARGS[buildroot]}/TARBALL/geowave-jace-${ARGS[vendor-version]}-$version-${githash:0:7}.tar.gz

echo '###### Copy rpm to repo and reindex'

LOCAL_REPO_DIR=/var/www/html/repos
cp -R ${WORKSPACE}/${ARGS[buildroot]}/RPMS/${ARGS[arch]}/*.rpm ${LOCAL_REPO_DIR}/${ARGS[repo]}/${ARGS[buildtype]}/${ARGS[arch]}/
cp -fR ${WORKSPACE}/${ARGS[buildroot]}/SRPMS/*.rpm ${LOCAL_REPO_DIR}/${ARGS[repo]}/${ARGS[buildtype]}/SRPMS/
cp -fR ${WORKSPACE}/${ARGS[buildroot]}/TARBALL/*.tar.gz ${LOCAL_REPO_DIR}/${ARGS[repo]}/${ARGS[buildtype]}/TARBALL/

# When several processes run createrepo concurrently they will often fail with problems trying to
# access index files that are in the process of being overwritten by the other processes. The command
# below uses two utilities that will cause calls to createrepo (from this script) to wait to gain an
# exclusive file lock before proceeding with a maximum wait time set at 10 minutes before they give
# up and fail. the ha* commands are from the hatools rpm available via EPEL.
/usr/bin/hatimerun -t 10:00 \
/usr/bin/halockrun /var/lock/subsys/rpmrepo \
/usr/bin/createrepo --update --workers 2 ${SNAPSHOT_DIR}/${ARGS[repo]}/${ARGS[buildtype]}/${ARGS[arch]}/