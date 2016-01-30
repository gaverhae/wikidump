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

The test pane is started with:

```bash
$ lein autoexpect
```

The REPL pane is started with:

```bash
$ lein repl
```

As long as the REPL is started in the correct folder, Vim (with fireplace)
should detect the REPL and connect to it automatically at the first command
that requires a REPL.

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
