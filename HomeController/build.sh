source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 17.0.7-tem
./gradlew --version
./gradlew ktlintFormat
./gradlew clean build bootJar