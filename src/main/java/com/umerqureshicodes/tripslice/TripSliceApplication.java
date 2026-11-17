package com.umerqureshicodes.tripslice;

import com.umerqureshicodes.tripslice.s3.S3Service;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class TripSliceApplication {

	public static void main(String[] args) {SpringApplication.run(TripSliceApplication.class, args);}


}
