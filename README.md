# table-spec

Specs ([clojure.spec](https://clojure.org/about/spec)) from SQL database schema for data generation and validation.

## Usage

```
> psql
postgres#= create table lol (id int not null, name varchar(250));
CREATE TABLE
```

```clojure
...
:dependencies [[org.clojure/clojure "1.9.0-alpha14"]
               [viesti/table-spec "0.1.0"]
               [org.postgresql/postgresql "9.4.1212"]]
; For generating test data
:profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0" :scope "test"]]}}
...


user> (require '[table-spec.core :as t])
nil
user> (require '[clojure.spec :as s])
nil
user> (-> {:connection-uri "jdbc:postgresql:lol" :schema "public"}
          (t/tables)
          (t/register))
nil
user> (s/exercise :table/lol)
([#:lol{:id -1, :name ""} #:lol{:id -1, :name ""}] [#:lol{:id 0, :name "C"} #:lol{:id 0, :name "C"}]...
user> (s/exercise :lol/id)
([-1 -1] [-1 -1] [1 1] [0 0] [-1 -1] [0 0] [-2 -2] [-1 -1] [-1 -1] [-8 -8])
```

## License

Copyright © 2017 Kimmo Koskinen

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
