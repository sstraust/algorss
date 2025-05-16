# AlgoRSS

AlgoRSS is an **open source RSS reader** focused on **customizability** and **algorithmic transparency**. 

You can use it on the website [here](https://tbd19201230912091209.com)

### Why AlgoRSS?
- It lets you **edit source code directly from your browser**. This means you can make changes even if you don't self-host.
- It's free and open source.
- It gives you more powerful tools than other popular RSS readers

## Customization and Use
### What is an AlgoRSS feed?
A **feed** is the main view that you see when you visit AlgoRSS. It is a list of articles from all the sources you follow.

### How do you define your own feed?
Typically a feed definition consists of three sections:
- Specify where to look for articles (what news sources do you follow?)
- Specify how to sort and filter those articles
- Specify how to display the articles that you found

### Specify who to follow
### Article Sources
An article source is a **source of information** that returns a list of recent news articles. An article source can be an RSS Feed, a YouTube Channel, or even a social media account (like BlueSky).

#### Unmanaged Article Sources
Unmanaged article sources are a *direct link* to an channel, or RSS feed. Once created, the link address cannot be changed.

#### Managed Article Sources
Managed article sources are a *unique identifier*, like "@NYTimes", that *points to* a channel or RSS feed. If you refer to a source by it's unique identifier, you can update the link address without needing to update the identifier.

This is especially useful if you want to refer to an idea, like "the new york times top stories", but you want it to be *stable*, even if the new york times updates the URL where their RSS feed is located. You can also rely on article sources managed by others.

### Specify how articles are sorted

## Running the Project
### Getting the Articles
To run the article fetcher, do:

```
python collect_news.py user_sources
```

This needs to run periodically (i.e. via a cronjob) to get the articles.
### Running Tests
during project setup, run
```
ln -sf hooks/pre-push .git/hooks/pre-push
```

to install the pre-push hooks into your repo

## Design Decisions
### MongoDB
I chose MongoDB because:
- I wanted something popular and well understood
- Returning JSON from the database makes it easy
  for users to write news-processing commands in javascript.
- Postgres doesn't support JSON schema enforcement (at time of writing)

I don't think MongoDB is the be-all end-all of databases, but it worked pretty well for this use-case.

### Document Embeddings
We need document embeddings in order to implement some ML based features. MongoDB _does_ have a document vector search, but it's not open source, and it's more expensive then other alternatives.

I wanted to pick something _reliable_ and _stable_ so that I can run this service until the end of time without maintenance or worrying about costs. It has to be so rock solid and stable that I don't need to touch it, and a cloud-only provider doesn't fit that bill.

For this project I went with Postgres and PGVector, along with a continuous update script that clears out old news from the table, with the goal of keeping the total table size under a million articles. PGVector doesn't scale well over a million articles. Even though this means it may not be able to index old news, it will be reliable and stable. Postgres also has really good filtering tools for creating combined queries, and Database _management_ in postgres is way better than the other alternatives.

I also considered Milvus, which is performant, and can be locally hosted, but decided that easier database maintenance meant that postgres would be more reliable for maintaining a table that I wanted to keep at a small size.

If we eventually decide to index all articles in AlgoRSS, not just the most recent ones, and we have the server capacity to do so, we will revisit this decision, and likely transfer our existing queries to Milvus. We will make an effort to hide most of our vector queries behind an API, so if we decide to make this change it will be easy to do so.

### Schemas
#### Article Source Schemas
Use URLs as source ids for anonymous sources

every anonymous source is uniquely defined, and tied to its link, so there is kind of by default a 1-1 mapping to use as an identifier




### Key Features
#### Customizable Themes
#### Customizable Display
#### NLP Tools, like Clustering and Topic Modelling
#### Custom Sorting, Filtering, and Ranking


