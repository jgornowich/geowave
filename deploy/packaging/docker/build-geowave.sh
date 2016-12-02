#!/bin/bash
#
# GeoWave Jenkins Build Script
#

echo "---------------------------------------------------------------"
echo "         Building GeoWave with the following settings"
echo "---------------------------------------------------------------"
echo "BUILD_ARGS=${BUILD_ARGS} ${@}"
echo "---------------------------------------------------------------"

# Throughout the build, capture jace artifacts to support testing
mkdir -p $WORKSPACE/deploy/target/geowave-jace/bin

# Build each of the "fat jar" artifacts and rename to remove any version strings in the file name

mvn package -am -pl deploy -P geotools-container-singlejar -Dgeotools.finalName=geowave-geoserver $BUILD_ARGS "$@"

mvn package -am -pl deploy -P accumulo-container-singlejar -Daccumulo.finalName=geowave-accumulo $BUILD_ARGS "$@"

mvn package -am -pl deploy -P hbase-container-singlejar -Dhbase.finalName=geowave-hbase $BUILD_ARGS "$@"

mvn package -am -pl deploy -P geowave-tools-singlejar -Dtools.finalName=geowave-tools $BUILD_ARGS "$@"

# Copy the tools fat jar
cp $WORKSPACE/deploy/target/geowave-tools.jar $WORKSPACE/deploy/target/geowave-jace/bin/geowave-tools.jar

# Run the Jace hack
cd $WORKSPACE
chmod +x $WORKSPACE/deploy/packaging/docker/install-jace.sh
$WORKSPACE/deploy/packaging/docker/install-jace.sh $BUILD_ARGS "$@"

cd $WORKSPACE
# Build the jace bindings
if [ ! -f $WORKSPACE/deploy/target/jace-source.tar.gz ]; then
    mvn package -am -pl deploy -P generate-geowave-jace $BUILD_ARGS "$@"
    mv $WORKSPACE/deploy/target/geowave-deploy*-jace.jar $WORKSPACE/deploy/target/geowave-jace/bin/geowave-runtime.jar
    cp $WORKSPACE/deploy/jace/CMakeLists.txt $WORKSPACE/deploy/target/geowave-jace
    cp -R $WORKSPACE/deploy/target/dependency/jace/source $WORKSPACE/deploy/target/geowave-jace
    cp -R $WORKSPACE/deploy/target/dependency/jace/include $WORKSPACE/deploy/target/geowave-jace
    tar -czf $WORKSPACE/deploy/target/geowave-jace.tar.gz -C $WORKSPACE/deploy/target/ geowave-jace
fi

# Build and archive HTML/PDF docs
if [ ! -f $WORKSPACE/target/site.tar.gz ]; then
    mvn javadoc:aggregate $BUILD_ARGS "$@"
    mvn -P docs -pl docs install $BUILD_ARGS "$@"
    tar -czf $WORKSPACE/target/site.tar.gz -C $WORKSPACE/target site
fi

# Build and archive the man pages
if [ ! -f $WORKSPACE/docs/target/manpages.tar.gz ]; then
    mkdir -p $WORKSPACE/docs/target/{asciidoc,manpages}
    cp -fR $WORKSPACE/docs/content/manpages/* $WORKSPACE/docs/target/asciidoc
    find $WORKSPACE/docs/target/asciidoc/ -name "*.txt" -exec sed -i "s|//:||" {} \;
    find $WORKSPACE/docs/target/asciidoc/ -name "*.txt" -exec a2x -d manpage -f manpage {} -D $WORKSPACE/docs/target/manpages \;
    tar -czf $WORKSPACE/docs/target/manpages.tar.gz -C $WORKSPACE/docs/target/manpages/ .
fi

## Copy over the puppet scripts
if [ ! -f $WORKSPACE/deploy/target/puppet-scripts.tar.gz ]; then
    tar -czf $WORKSPACE/deploy/target/puppet-scripts.tar.gz -C $WORKSPACE/deploy/packaging/puppet geowave
fi