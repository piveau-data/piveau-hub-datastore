FROM openjdk:jre-alpine
#LABEL io.openshift.expose-services="8080:8080"
ENV VERTICLE_FILE data-upload-fat.jar
# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080
RUN addgroup -S vertx && adduser -S -g vertx vertx

# Copy your fat jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/

RUN chown -R vertx $VERTICLE_HOME && chmod -R g+w $VERTICLE_HOME
USER vertx

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec java $JAVA_OPTS -jar $VERTICLE_FILE "]
#CMD ["exec java $JAVA_OPTS -jar $VERTICLE_FILE -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory -Dvertx.metrics.options.enabled=true"]