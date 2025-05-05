tasks.register("publish") {
    dependsOn(gradle.includedBuild("e2immu-java-bytecode").task(":publish"))
    dependsOn(gradle.includedBuild("e2immu-java-parser").task(":publish"))
}
tasks.register("publishToMavenLocal") {
    dependsOn(gradle.includedBuild("e2immu-java-bytecode").task(":publishToMavenLocal"))
    dependsOn(gradle.includedBuild("e2immu-java-parser").task(":publishToMavenLocal"))
}
