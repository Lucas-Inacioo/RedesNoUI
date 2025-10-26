## Minimal Run:
java -jar target/unicast-demo.jar --self <id>

## Flags
--self <id> Node’s UCSAP id (short integer). REQUIRED.
--config <path> Path to Unicast protocol config file. Default: /up.conf (may be a .txt)
--lang <code> Language for messages: en or pt. Default: en

## Useful Commands
- Compile: mvn clean compile
- All tests: mvn test
- Specific tests: mvn -Dtest=TestClass
- Generate javadoc: mvn javadoc:javadoc
- Generate jar using profile: mvn -P demo -DskipTests clean package