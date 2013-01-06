# Arrowlets

Arrowlets is a ClojureScript library using concept of arrows,
attempting to make composing event-handlers easier. It is port (or
rather, a draft of a port at this time) of the library described in the
paper [Directing JavaScript with Arrows][paper]. 

# Goals

Determine if Arrows will indeed make the code simpler and more
composable. 

After I finalize the first version, I'll try to use it in a project
and report my findings.

# How to run it

Just clone it and run:

     lein repl
     (run)

And also in another shell run:

    lein cljsbuild auto

And go to http://localhost:3500/. Also take a look at the javascript
console. 

I recommend reading the [paper][paper] along with the code as
otherwise it may be hard to follow.

[paper]:
http://www.cs.umd.edu/projects/PL/arrowlets/pdfs/Directing%20JavaScript%20with%20Arrows%20(DLS%202009).pdf
[site]: http://www.cs.umd.edu/projects/PL/arrowlets/
