Create an experiment that compares component scanning using the Reflection API with scanning using ASM bytecode inspection. Your goal is to demonstrate why ASM is preferable for component scanning.

Create a package containing 500–2000 classes, some annotated with a custom @MyComponent annotation.
Implement a Reflection-based scanner using Class.forName(...) and an ASM-based scanner that inspects .class files without loading the classes.
Verify that both scanners discover the same @MyComponent classes.
Add classes with static initialization blocks and determine whether scanning causes class loading or initialization.
Compare the execution time of the two approaches using warm-up runs followed by multiple measured runs.
Use JVM class-loading logs, such as -Xlog:class+load, to compare how many classes are loaded.
Include a class with a dependency that is unavailable at runtime and compare how the two scanners behave.
Summarize your results in a table containing execution time, classes loaded, static initialization behavior, and behavior with missing dependencies.