tasks.register("publish") {
    dependsOn(gradle.includedBuild("e2immu-java-bytecode").task(":publish"))
    dependsOn(gradle.includedBuild("e2immu-java-parser").task(":publish"))
}
