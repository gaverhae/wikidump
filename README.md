# wikidump

This is not a serious project. Please do not try to use it for anything. The
sole purpose of this project is for me to play with and explore some
technologies related to Clojure, JSON and text search.

Should you take the time to look at the code, please do not hesitate to suggest
improvements; in fact, this is the very reason this project is public.

## Usage

At this point, the server is launched with:

```bash
$ lein run
```

This starts up a web server on `localhost:8080` that will respond to requests.
It also starts a background thread that downloads an excerpt from the Wikipedia
abstracts database, parses it, indexes it, and adds it to an internal in-memory
database.

This is obviously a very temporary solution to get something working quickly;
obvious missing pieces are loading data from an existing file instead of
downloading it each time and more elaborate backends than an in-memory map.

## Development

I work in Vim and tmux. I usually have one visible pane with tests running, one
pane with my Vim editor, and one hidden pane with the REPL the editor is
connected to.

My development workflow also uses Docker, in particular the `docker-compose`
tool.

The test pane is started with:

```bash
$ docker-compose -f docker/autotest.yml up
```

The REPL pane is started similarly with:

```bash
$ docker-compose -f docker/repl.yml up
```

The editor then has to connect to the REPL. In my case (Vim on a Mac, so Docker
runs inside a VM) this is done with the following command (inside Vim):

```vim
:Connect nrepl://192.168.99.100:10345
```

Of course, this is because my Docker VM has the IP `192.168.99.100`, which may
or may not match yours.

## Options

FIXME: listing of options this app accepts.

## Examples

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2016 Gary Verhaegn

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
