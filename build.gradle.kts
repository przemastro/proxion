plugins {
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("io.freefair.lombok") version "8.4"
}

group = "pl.proxion"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("pl.proxion.MainApp")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

javafx {
    version = "17.0.12"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    // JavaFX
    implementation("org.openjfx:javafx-controls:17.0.12")
    implementation("org.openjfx:javafx-fxml:17.0.12")

    // Netty
    implementation("io.netty:netty-all:4.1.104.Final")

    // HTTP Client - ZAKTUALIZOWANE
    implementation("org.apache.httpcomponents:httpclient:4.5.14") {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("org.apache.httpcomponents:httpcore:4.4.16")
    implementation("org.apache.httpcomponents:httpmime:4.5.14")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    // Logging
    implementation("org.apache.logging.log4j:log4j-core:2.22.1")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}