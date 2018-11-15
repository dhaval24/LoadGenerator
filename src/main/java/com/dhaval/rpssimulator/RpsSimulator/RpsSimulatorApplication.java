package com.dhaval.rpssimulator.RpsSimulator;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class RpsSimulatorApplication implements CommandLineRunner {

	@Autowired
	TelemetryClient client;

	private volatile static int currentBatch = 0;
	private static long startTime;

	public static void main(String[] args) {
		SpringApplication.run(RpsSimulatorApplication.class, args);
	}

	// Expects the RPS argument for load generation.
	@Override
	public void run(String... args) throws Exception {
		// the overall number of items to send
		final int telemetryItemCount = Integer.MAX_VALUE;

		// how many items to send each second
		final int telemetryItemsPerSecond = StringUtils.isNoneBlank(args[0]) ? Integer.parseInt(args[0]) : 100;

		// the size (in items) of a batch
		// if the concept of a batch is not applicable, set to 1
		final int batchSizeInTelemetryItems = 1;

		System.out.println(String.format("sending %s items in batches of %s at a rate of %s items per second..." ,
				telemetryItemCount, batchSizeInTelemetryItems, telemetryItemsPerSecond));

		CompletableFuture.supplyAsync(() -> {
			long lastUpdate = TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
			int lastBatch = 0;
			while (true) {

				try {
					TimeUnit.SECONDS.sleep(1);

					int localCurrentBatch = currentBatch;
					long localNow = TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
					double timeElapsedSinceLastUpdate = localNow - lastUpdate;

					if (timeElapsedSinceLastUpdate > 0) {

						double var1 = (((localCurrentBatch - lastBatch) * batchSizeInTelemetryItems) / (timeElapsedSinceLastUpdate));
						double var2 = (currentBatch * batchSizeInTelemetryItems);
						System.out.println(String.format("Time elapsed %s, Items per second %s, Items sent %s", timeElapsedSinceLastUpdate,
								var1,
								var2));
					}

					lastUpdate = localNow;
					lastBatch = localCurrentBatch;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		});

		// overall batches to send
		int numBatches = (int)Math.floor((double)telemetryItemCount / batchSizeInTelemetryItems);

		long expectedTimeToSendEverything = TimeUnit.SECONDS.toSeconds(numBatches * batchSizeInTelemetryItems / telemetryItemsPerSecond);

		startTime = System.currentTimeMillis();

		long estimatedEndTime = TimeUnit.SECONDS.convert(startTime, TimeUnit.MILLISECONDS) + expectedTimeToSendEverything;

		for (currentBatch = 0; currentBatch < numBatches; ++currentBatch) {

			// pause if we're ahead of schedule
			int telemetryItemsLeft = (numBatches - currentBatch) * batchSizeInTelemetryItems;

			long estimatedTimeLeftBasedOnOriginalSchedule = estimatedEndTime - TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);

			long estimatedTimeLeftBasedOnProgressSoFar = TimeUnit.SECONDS.toSeconds(telemetryItemsLeft / telemetryItemsPerSecond);

			// if we are ahead of the schedule, slow down
			// if we are behind the schedule, proceed forward at full speed
			// note that if the schedule is impossible to keep up with we'll just do our best, but not skip any items

			if (estimatedTimeLeftBasedOnProgressSoFar < estimatedTimeLeftBasedOnOriginalSchedule) {
				try {
					TimeUnit.SECONDS.sleep(estimatedTimeLeftBasedOnOriginalSchedule - estimatedTimeLeftBasedOnProgressSoFar);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			}

			//send items here
			Random random = new Random();
			int id = random.nextInt();
			Map<String, String> customDimensions = new HashMap<>();
			customDimensions.put("dimension", String.valueOf(id));

			client.trackTrace("sent a trace with id " + id, SeverityLevel.Error);
		}
	}
}
