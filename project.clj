(defproject com.github.danlentz/clj-figlet "0.1.1"
  :description  "A native Clojure re-implementation of FIGlet, the classic
                 ASCII art text renderer.  Parses FIGfont Version 2 font
                 files (Cowan & Burton, 1996-97), implements all six
                 horizontal smushing rules plus universal smushing, and
                 produces output identical to the reference C figlet.
                 No external dependencies beyond Clojure itself."
  :author       "Dan Lentz"
  :url          "https://github.com/danlentz/clj-figlet"
  :signing      {:gpg-key "400A8A1D768AF714C4E7638670A3B82A62662999"}
  :scm          {:name "git" :url "https://github.com/danlentz/clj-figlet"}
  :license      {:name "Eclipse Public License"
                 :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2" :scope "provided"]]
  :plugins      [[lein-cloverage "1.2.4"]
                 [lein-codox "0.10.8"]]
  :codox        {:output-path "doc/api"
                 :src-dir-uri "https://github.com/danlentz/clj-figlet/blob/master/"
                 :doc-files   []
                 :src-linenum-anchor-prefix "L"
                 :project     {:name "clj-figlet"}}
  :global-vars  {*warn-on-reflection* true}
  :repl-options {:init-ns clj-figlet.core})
