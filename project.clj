(defproject therasppie/tree "0.0.1-SNAPSHOT"
  :description "A minecraft server library implementing the core features of the game engine."
  :url "https://github.com/therasppie/tree"
  :dependencies [[org.clojure/clojure "1.10.0-alpha6"]
                 [better-cond "1.0.1"]
                 [org.clojure/tools.cli "0.3.5"]
                 [io.netty/netty-all "4.1.24.Final"]
                 [org.clojure/data.json "0.2.6"]
                 [it.unimi.dsi/fastutil "8.1.0"]
                 [org.hdrhistogram/HdrHistogram "2.1.10"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [criterium "0.4.4"]]}
             :direct-linking {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :profiling {:jvm-opts ["-XX:+UnlockDiagnosticVMOptions" "-XX:+DebugNonSafepoints"]}}
  :java-source-paths #{"src"}
  :javac-options ["-Xlint:unchecked"]
  :global-vars {*warn-on-reflection* true}
  :aot :all
  :main therasppie.tree.main)
