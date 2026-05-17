[![CI](https://github.com/naruoga/jOpenDocument/actions/workflows/ci.yml/badge.svg?branch=3.0)](https://github.com/naruoga/jOpenDocument/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/naruoga/jOpenDocument.svg)](https://jitpack.io/#naruoga/jOpenDocument)

To compile and run tests use Maven :

    mvn test

To build the jars :

    mvn package

Google docs
-----------
jOpenDocument created documents should be compatible but (at least for spreadsheets) Google only accepts lower case automatic style names.
It also only supports lengths in centimetres (at least for column widths) but this is now the default for TableStyle.getWriteUnit().
