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
wget http://apache.mesi.com.ar/lucene/solr/4.5.0/solr-4.5.0.zip
unzip solr-4.5.0.zip
cp solr-4.5.0/dist/solr-4.5.0.war /var/lib/tomcat7/webapps/
```

Setting up the application:
```
git clone https://github.com/garysieling/solr-git.git
```
