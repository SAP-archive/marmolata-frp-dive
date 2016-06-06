compile:
	sbt -J-Xms4g -J-Xmx8g -Dsbt.log.format=false compile
cont:
	sbt -J-Xms4g -J-Xmx8g -Dsbt.log.format=false ~compile
test:
	sbt -J-Xms4g -J-Xmx8g test
sbt:
	sbt -J-Xms4g -J-Xmx8g
clean:
	sbt clean clean-files
console:
	sbt -J-Xms4g -J-Xmx8g console
scalastyle:
	sbt scalastyle
doc:
	sbt -J-Xms4g -J-Xmx8g genDocs
publish:
	sbt -J-Xms4g -J-Xmx8g -Dsbt.log.format=false publish

