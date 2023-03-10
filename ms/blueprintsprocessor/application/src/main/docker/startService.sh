#!/bin/sh

TRUSTSTORE_PASSWD=${TRUSTSTORE_PASSWD:-changeit}

nodeName=BlueprintsProcessor_1.0.0_$(cat /proc/self/cgroup | grep docker | sed s/\\//\\n/g | tail -1)

export APP_HOME=/opt/app/onap

#echo "${CLUSTER_ID}:${CLUSTER_NODE_ID} APP Config HOME : ${APP_CONFIG_HOME}"
#[[ - $APP_CONFIG_HOME/${PRIVATE_CA} ]] && keytool -import -noprompt -trustcacerts -keystore $JAVA_HOME/lib/security/cacerts -storepass ${TRUSTSTORE_PASSWD} -alias ${PRIVATE_CA} -file $APP_CONFIG_HOME/${PRIVATE_CA}

#Instead of above, using cert-initializer truststore to replace the default java cacerts
[[ -f $AAF_CREDSPATH/truststoreONAPall.jks ]] && cp $AAF_CREDSPATH/truststoreONAPall.jks $JAVA_HOME/lib/security/cacerts 

exec java -classpath "/etc:${APP_HOME}/lib/*:/lib/*:/src:/schema:/generated-sources:${APP_CONFIG_HOME}:${APP_HOME}" \
-DappName=${APP_NAME} -DappVersion=${BUNDLEVERSION} \
-DrouteOffer=${ROUTEOFFER} \
-DVERSION_ROUTEOFFER_ENVCONTEXT=${BUNDLEVERSION}/${STICKYSELECTORKEY}/${ENVCONTEXT} \
-DSecurityFilePath=/etc \
-DREST_NAME_NORMALIZER_PATTERN_FILE=/etc/PatternInputs.txt \
-Dms_name=org.onap.ccsdk.cds.blueprintsprocessor \
-Dlogging.config=${APP_CONFIG_HOME}/logback.xml \
-Djava.security.egd=file:/dev/./urandom \
-DAPPNAME=${APP_NAME} -DAPPENV=${APP_ENV} -DAPPVERSION=${APP_VERSION} -DNAMESPACE=${NAMESPACE} \
-Dspring.config.location=${APP_CONFIG_HOME}/ \
-Dhazelcast.shutdownhook.policy=GRACEFUL \
-Dhazelcast.graceful.shutdown.max.wait=600 \
org.onap.ccsdk.cds.blueprintsprocessor.BlueprintProcessorApplicationKt
