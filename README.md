# solr-git [![Build status](https://travis-ci.org/rcook/solr-git.png)](https://travis-ci.org/rcook/solr-git)

About
=====

Convert git commit history to solr index

See http://garysieling.com/blog/converting-git-commit-history-to-a-solr-full-text-index for more information

Talk: https://rawgithub.com/garysieling/git-solr-talk/master/index.html

Setup
=====

Setup pre-requisites:
```
apt-get install openjdk-7-jdk git-core tomcat7 unzip
```

Solr setup:
```
cd ~
wget http://apache.mesi.com.ar/lucene/solr/4.5.1/solr-4.5.1.zip
unzip solr-4.5.1.zip
cd /solr-4.5.1/example
java -jar start.jar
```

Setting up the application:
```
git clone https://github.com/garysieling/solr-git.git
```

Modify the URL to point to your local Solr server, if needed (i.e. if you changed the default port or ran it on a different matchine).

Edit the location of the local git files you want indexed.

This can be run from Eclipse, but you need to add jars from the Solr installation.
