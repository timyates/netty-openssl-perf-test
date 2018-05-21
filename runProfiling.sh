#!/bin/bash

# Profile empty response
./gradlew profile -Ptype=empty
./gradlew profile -Ptype=empty -Pjdk

# Profile 5MB response
./gradlew profile -Psize=5 -Ptype=file
./gradlew profile -Psize=5 -Ptype=file -Pjdk

# Profile 20MB response
./gradlew profile -Psize=20 -Ptype=file
./gradlew profile -Psize=20 -Ptype=file -Pjdk