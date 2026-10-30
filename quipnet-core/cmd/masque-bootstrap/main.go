package main

import (
	"flag"
	"fmt"
	"os"
	"strings"

	quipnet "quipnet-core/engine"
)

func main() {
	accessToken := flag.String("token", "", "WARP access token")
	deviceID := flag.String("device-id", "", "WARP device id")
	deviceName := flag.String("device-name", "QuipNet Android", "MASQUE device name")
	existing := flag.String("existing", "", "existing MASQUE identity JSON")
	flag.Parse()

	if strings.TrimSpace(*accessToken) == "" || strings.TrimSpace(*deviceID) == "" {
		fmt.Fprintln(os.Stderr, "token and device-id are required")
		os.Exit(2)
	}

	result, err := quipnet.EnsureMasqueConfig(*existing, *accessToken, *deviceID, *deviceName)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	fmt.Println(result)
}
