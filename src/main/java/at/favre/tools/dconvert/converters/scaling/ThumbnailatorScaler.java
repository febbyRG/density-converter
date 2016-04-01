package at.favre.tools.dconvert.converters.scaling;

import at.favre.tools.dconvert.arg.EScalingQuality;
import at.favre.tools.dconvert.arg.ImageType;
import at.favre.tools.dconvert.util.ImageUtil;
import net.coobird.thumbnailator.makers.FixedSizeThumbnailMaker;
import net.coobird.thumbnailator.resizers.AbstractResizer;
import net.coobird.thumbnailator.resizers.DefaultResizerFactory;
import net.coobird.thumbnailator.resizers.Resizer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;

/**
 * Using https://github.com/coobird/thumbnailator to scale
 */
public class ThumbnailatorScaler extends ABufferedImageScaler {
    @Override
    public BufferedImage scale(BufferedImage imageToScale, int dWidth, int dHeight, ImageType.ECompression compression, EScalingQuality algorithm, Color background, boolean antiAlias) {
        BufferedImage scaledImage = null;
        if (imageToScale != null) {

            Resizer resizer = translate(algorithm, new Dimension(imageToScale.getWidth(), imageToScale.getHeight()), new Dimension(dWidth, dHeight));

            scaledImage = new FixedSizeThumbnailMaker(dWidth, dHeight, false, true)
                    .resizer(resizer).make(imageToScale);

            if (antiAlias) {
                scaledImage = ImageUtil.OP_ANTIALIAS.filter(scaledImage, null);
            }

            if (!compression.hasTransparency) {
                BufferedImage convertedImg = new BufferedImage(scaledImage.getWidth(), scaledImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                convertedImg.getGraphics().drawImage(scaledImage, 0, 0, background, null);
                scaledImage = convertedImg;
            }
        }
        return scaledImage;
    }

    private Resizer translate(EScalingQuality algorithm, Dimension image, Dimension target) {
        switch (algorithm) {
            default:
            case HIGH_QUALITY:
                return DefaultResizerFactory.getInstance().getResizer(image, target);
            case BALANCE:
                return new ProgressiveResizer(RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            case SPEED:
                return new NearestNeighborResizer();
        }
    }

    public static class NearestNeighborResizer extends AbstractResizer {
        public NearestNeighborResizer() {
            this(Collections.<RenderingHints.Key, Object>emptyMap());
        }

        public NearestNeighborResizer(Map<RenderingHints.Key, Object> hints) {
            super(RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR, hints);
        }


        @Override
        public void resize(BufferedImage srcImage, BufferedImage destImage) throws NullPointerException {
            super.resize(srcImage, destImage);
        }
    }

    public static class ProgressiveResizer extends AbstractResizer {
        public ProgressiveResizer(Object interpolationValue) {
            this(interpolationValue, Collections.emptyMap());
        }

        public ProgressiveResizer(Object interpolationValue, Map<RenderingHints.Key, Object> hints) {
            super(interpolationValue, hints);
            checkArg(interpolationValue);
        }

        private void checkArg(Object interpolationValue) {
            if (interpolationValue != RenderingHints.VALUE_INTERPOLATION_BICUBIC &&
                    interpolationValue != RenderingHints.VALUE_INTERPOLATION_BILINEAR &&
                    interpolationValue != RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                    )
                throw new IllegalArgumentException("wrong argument passed muts be one of RenderingHints.VALUE_INTERPOLATION_BICUBIC, " +
                        "RenderingHints.VALUE_INTERPOLATION_BILINEAR or RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR");
        }

        @Override
        public void resize(BufferedImage srcImage, BufferedImage destImage)
                throws NullPointerException {
            super.performChecks(srcImage, destImage);

            int currentWidth = srcImage.getWidth();
            int currentHeight = srcImage.getHeight();

            final int targetWidth = destImage.getWidth();
            final int targetHeight = destImage.getHeight();

            // If multi-step downscaling is not required, perform one-step.
            if ((targetWidth * 2 >= currentWidth) && (targetHeight * 2 >= currentHeight)) {
                super.resize(srcImage, destImage);
                return;
            }

            // Temporary image used for in-place resizing of image.
            BufferedImage tempImage = new BufferedImage(
                    currentWidth,
                    currentHeight,
                    destImage.getType()
            );

            Graphics2D g = tempImage.createGraphics();
            g.setRenderingHints(RENDERING_HINTS);
            g.setComposite(AlphaComposite.Src);

		/*
         * Determine the size of the first resize step should be.
		 * 1) Beginning from the target size
		 * 2) Increase each dimension by 2
		 * 3) Until reaching the original size
		 */
            int startWidth = targetWidth;
            int startHeight = targetHeight;

            while (startWidth < currentWidth && startHeight < currentHeight) {
                startWidth *= 2;
                startHeight *= 2;
            }

            currentWidth = startWidth / 2;
            currentHeight = startHeight / 2;

            // Perform first resize step.
            g.drawImage(srcImage, 0, 0, currentWidth, currentHeight, null);

            // Perform an in-place progressive bilinear resize.
            while ((currentWidth >= targetWidth * 2) && (currentHeight >= targetHeight * 2)) {
                currentWidth /= 2;
                currentHeight /= 2;

                if (currentWidth < targetWidth) {
                    currentWidth = targetWidth;
                }
                if (currentHeight < targetHeight) {
                    currentHeight = targetHeight;
                }

                g.drawImage(
                        tempImage,
                        0, 0, currentWidth, currentHeight,
                        0, 0, currentWidth * 2, currentHeight * 2,
                        null
                );
            }

            g.dispose();

            // Draw the resized image onto the destination image.
            Graphics2D destg = destImage.createGraphics();
            destg.drawImage(tempImage, 0, 0, targetWidth, targetHeight, 0, 0, currentWidth, currentHeight, null);
            destg.dispose();
        }
    }
}