Project developed for Mobile and Social Sensing System course at M.S Course in Computer Engineering @ University of Pisa

Link to the GitHub repo: https://github.com/loremancio/MSSSProject/

# NoiseCity
A crowdsensing platform for mapping urban noise pollution via smartphones. It addresses the challenge of monitoring urban noise pollution, which is often limited by the high cost and sparse coverage of traditional sensing infrastructures. NoiseCity provides a low-cost, scalable, and interactive solution by leveraging the microphones and GPS sensors already present in users' smartphones.

## How It Works
NoiseCity operates on a client-server model, enabling a community-driven approach to data collection:
1. **Record**: users use the Android application to record audio samples through their smartphone's microphone. The app calculates the sound level in decibels (dB FS) using RMS on one-second windows.
2. **Calibrate:** to account for hardware differences between devices, the app allows users to set a manual calibration offset (in dB).
3. **Upload**: to protect user privacy and minimize bandwidth, the app does not send the raw audio recording. Instead, it only uploads the aggregated data: the calculated volume, a timestamp, and the user's current location (acquired via Fused Location Provider).
4. **Process**: a Flask backend server receives this data and stores it in a MongoDB database. The server aggregates measurements using Geohash encoding to prepare them for efficient visualization.
5. **Visualize**: users can explore all the collected data on an interactive heatmap, allowing them to see noise intensity across the city and filter it by time of day.

## Key Features
### Sound Recorder & Analyzer
* **Real-time Waveform**: displays the time-domain waveform of the sound being captured.
* **Real-time Spectrum (FFT)**: shows the frequency-domain spectrum of the audio in real-time, computed via a Fast Fourier Transform (FFT).
* **Calibration**: a dedicated setting allows users to add a decibel offset to calibrate their specific microphone.

### Interactive Noise Heatmap
* **Google Maps Integration**: renders all aggregated noise data as an intuitive heatmap layer on top of Google Maps.
* **Time Filtering**: users can select a custom start and end date/time to visualize noise levels for a specific time range.
* **Dynamic Loading**: the map dynamically updates the query as the user pans and zooms, ensuring relevant data is always displayed.

### Gamification & User Profile
To encourage active participation and improve the spatial coverage of recordings, the app includes a gamification system.
* **Achievements**: users can earn badges for their contributions, such as "City Explorer," "World Traveler," and "Measurement Master".
* **Personal Statistics**: the user profile page displays personalized stats, including the total time the user has been exposed to high and low noise levels based on their own recordings.
