package de.devsurf.twiddns;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import twitter4j.AsyncTwitter;
import twitter4j.AsyncTwitterFactory;
import twitter4j.Status;
import twitter4j.TwitterAdapter;
import twitter4j.TwitterException;
import twitter4j.TwitterMethod;
import twitter4j.http.AccessToken;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import de.devsurf.injection.guice.configuration.Configuration;
import de.devsurf.injection.guice.configuration.PathConfig;
import de.devsurf.injection.guice.configuration.Configuration.Type;
import de.devsurf.injection.guice.configuration.features.ConfigurationFeature;
import de.devsurf.injection.guice.scanner.PackageFilter;
import de.devsurf.injection.guice.scanner.StartupModule;
import de.devsurf.injection.guice.scanner.asm.ASMClasspathScanner;
import de.devsurf.twiddns.connect.AccessTokenProvider;

@Configuration(location=@PathConfig("/configuration.properties"), alternative=@PathConfig("/configuration.override.properties"), type=Type.VALUES)
public class Publisher {
	private static final Logger LOGGER = Logger.getLogger(Publisher.class.getName());
	private static final String CONSUMER_KEY = "{replace with key}";
	private static final String CONSUMER_SECRET = "{replace with key}";

	private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("HH:mm:ss");
	
	private AtomicInteger counter = new AtomicInteger();
	private List<Tweeter> tweeters = new ArrayList<Tweeter>();
	private AccessToken token;
	
	private AsyncTwitterFactory factory = new AsyncTwitterFactory(new TwitterAdapter() {
		@Override
		public void onException(TwitterException e, TwitterMethod method) {
			LOGGER.warning("Exception updating Twitter status: " + e.toString()+" Twitter says it is: "+e.getMessage());
			counter.decrementAndGet();
		}

		@Override
		public void updatedStatus(Status statuses) {
			LOGGER.info("Updated Twitter status: " + statuses.getText());
			counter.decrementAndGet();
		}
	});
	
	@Inject
	public Publisher(Set<Tweeter> implementations, AccessTokenProvider provider) {
		super();
		this.tweeters = new ArrayList<Tweeter>(implementations);
		this.token = provider.get();
	}
	
	public void tweet() throws Exception{
		AsyncTwitter twitter =  factory.getOAuthAuthorizedInstance(CONSUMER_KEY, CONSUMER_SECRET, token);
		for(Tweeter tweeter : tweeters){
			List<String> tweets = tweeter.tweet();
			for(String tweet : tweets){
				twitter.updateStatus(FORMATTER.format(new Date())+" - "+tweet);	
				counter.incrementAndGet();
			}
		}
		while(counter.get() > 0){
			Thread.sleep(1000);
		}
		twitter.shutdown();
	}
	
	public static void main(String[] args) throws Exception {
		StartupModule startup = StartupModule.create(ASMClasspathScanner.class,
			PackageFilter.create(Publisher.class));
		startup.addFeature(ConfigurationFeature.class);
		Injector injector = Guice.createInjector(startup);
		
		Publisher publisher = injector.getInstance(Publisher.class);
		publisher.tweet();
	}
}
