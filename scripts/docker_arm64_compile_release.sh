DOCKER_IMAGE_NAME=barbatos-linux-amd64

docker build -f Dockerfile -t $DOCKER_IMAGE_NAME .

./gradlew compileAgents

docker run --rm \
  -v $(pwd):/app \
  -v ~/.gradle:/root/.gradle \
  -v ~/.konan/dependencies:/root/.konan/dependencies \
  $DOCKER_IMAGE_NAME \
  ./gradlew linkReleaseExecutableLinuxX64 --no-daemon