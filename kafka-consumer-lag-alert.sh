#!/bin/bash

program_name="kafka-consumer-lag-alert"
version="0.0.1"

display_help() {
    echo "Usage: $0 [option...]" >&2
    echo ""
    echo "   -h, --help                 Displays script usage info"
    echo "   -e, --env                  The Kafka environment to connect to (application-<env>.yaml)"
    echo ""
    exit 1
}

env=""

# find argument names and values passed into the program
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h|--help) display_help; shift ;;
        -e|--env) env="$2"; shift ;;
        *) echo "Unknown parameter passed: $1" ;;
    esac
    shift
done

# verify required arguments were passed
[[ $env == "" ]] && { echo "The option --env (-e) is required but not set, please specify the environment name"  && display_help && exit 1; }

# execute program
java -cp "$program_name-$version.jar" -Dloader.main=com.jnj.kafka.admin.lagalert.Application -Dspring.profiles.active=$env -Dspring.config.location=conf/ -Dlogging.config=conf/logback.xml org.springframework.boot.loader.PropertiesLauncher